package ru.trafficmarkering.service.views.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.fraud.FraudCheckService;
import ru.trafficmarkering.service.social.SocialIntegration;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewsSyncServiceImplTest {

    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final SaverApplication saverApplication = mock(SaverApplication.class);
    private final SaverViewSnapshot saverViewSnapshot = mock(SaverViewSnapshot.class);
    private final FraudCheckService fraudCheckService = mock(FraudCheckService.class);
    private final ViewCountProvider provider = mock(ViewCountProvider.class);

    private final ViewsSyncServiceImpl service = new ViewsSyncServiceImpl(getterApplication,
            new ViewCountProviders(List.of(provider), new SocialIntegration(false)),
            new ViewsRecorder(getterApplication, saverApplication, saverViewSnapshot, fraudCheckService));

    private final User creator = User.builder().id(7L).name("Креатор").role(Role.CREATOR).build();

    @BeforeEach
    void setUp() {
        when(provider.platform()).thenReturn(Platform.YOUTUBE_SHORTS);
        when(provider.source()).thenReturn(ViewSource.YOUTUBE_API);
        when(provider.isConfigured()).thenReturn(true);
        when(saverApplication.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void captureWritesViewsIntoLockedRowAndKeepsItsMoneyAndStatus() {
        Campaign campaign = campaign();
        Application stale = application(campaign, "https://youtu.be/a", ApplicationStatus.PENDING, 0L);
        Application locked = application(campaign, "https://youtu.be/a", ApplicationStatus.REJECTED, 500_00L);
        locked.setId(stale.getId());
        when(getterApplication.getById(stale.getId())).thenReturn(Optional.of(stale));
        when(getterApplication.getByIdForUpdate(stale.getId())).thenReturn(Optional.of(locked));
        when(saverViewSnapshot.save(any(ApplicationViewSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(provider.fetchViews(anyLong(), anyCollection()))
                .thenReturn(Map.of("https://youtu.be/a", ViewCount.total(250L)));

        ApplicationViewSnapshot snapshot = service.captureNow(stale.getId()).snapshot();

        assertThat(snapshot.getViews()).isEqualTo(250L);
        assertThat(snapshot.getSource()).isEqualTo(ViewSource.YOUTUBE_API);
        assertThat(locked.getViews()).isEqualTo(250L);
        assertThat(locked.getCreditedKopecks()).isEqualTo(500_00L);
        assertThat(locked.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(stale.getViews()).isEqualTo(100L);
        verify(saverApplication).save(locked);
        verify(saverApplication, never()).save(stale);
    }

    @Test
    void captureFailsWhenPlatformGivesNoViews() {
        Application application = application(campaign(), "https://youtu.be/a", ApplicationStatus.PENDING, 0L);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));
        when(provider.fetchViews(anyLong(), anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.captureNow(application.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("не отдала просмотры");

        verify(saverViewSnapshot, never()).save(any());
        verify(saverApplication, never()).save(any());
    }

    @Test
    void captureFailsWhenApplicationDeletedBeforeLock() {
        Application gone = application(campaign(), "https://youtu.be/a", ApplicationStatus.PENDING, 0L);
        when(getterApplication.getById(gone.getId())).thenReturn(Optional.of(gone));
        when(getterApplication.getByIdForUpdate(gone.getId())).thenReturn(Optional.empty());
        when(provider.fetchViews(anyLong(), anyCollection()))
                .thenReturn(Map.of("https://youtu.be/a", ViewCount.total(250L)));

        assertThatThrownBy(() -> service.captureNow(gone.getId()))
                .isInstanceOf(ResponseStatusException.class);

        verify(saverViewSnapshot, never()).save(any());
        verify(saverApplication, never()).save(any());
    }

    private Campaign campaign() {
        return Campaign.builder().id(UUID.randomUUID()).title("Ролик про кофе").build();
    }

    private Application application(Campaign campaign, String videoUrl, ApplicationStatus status,
                                    long creditedKopecks) {
        return Application.builder().id(UUID.randomUUID()).campaign(campaign).creator(creator)
                .platform(Platform.YOUTUBE_SHORTS).videoUrl(videoUrl).status(status).views(100L)
                .accruedKopecks(creditedKopecks).creditedKopecks(creditedKopecks).build();
    }
}
