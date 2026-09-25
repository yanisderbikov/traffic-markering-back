package ru.trafficmarkering.service.views.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.fraud.FraudCheckService;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewsSyncServiceImplTest {

    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final SaverApplication saverApplication = mock(SaverApplication.class);
    private final SaverViewSnapshot saverViewSnapshot = mock(SaverViewSnapshot.class);
    private final FraudCheckService fraudCheckService = mock(FraudCheckService.class);
    private final CampaignAccrualService campaignAccrualService = mock(CampaignAccrualService.class);
    private final ViewCountProvider provider = mock(ViewCountProvider.class);

    private final ViewsSyncServiceImpl service = new ViewsSyncServiceImpl(getterApplication,
            new ViewCountProviders(List.of(provider)),
            new ViewsRecorder(getterApplication, saverApplication, saverViewSnapshot, fraudCheckService),
            campaignAccrualService);

    private final User creator = User.builder().id(7L).name("Криатор").role(Role.CREATOR).build();

    @BeforeEach
    void setUp() {
        when(provider.platform()).thenReturn(Platform.YOUTUBE_SHORTS);
        when(provider.source()).thenReturn(ViewSource.YOUTUBE_API);
        when(provider.isConfigured()).thenReturn(true);
        when(saverApplication.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void syncWritesViewsIntoLockedRowAndKeepsItsMoneyAndStatus() {
        Campaign campaign = campaign();
        Application stale = application(campaign, "https://youtu.be/a", ApplicationStatus.APPROVED, 0L);
        Application locked = application(campaign, "https://youtu.be/a", ApplicationStatus.REJECTED, 500_00L);
        locked.setId(stale.getId());
        when(getterApplication.getApproved()).thenReturn(List.of(stale));
        when(getterApplication.getByIdForUpdate(stale.getId())).thenReturn(Optional.of(locked));
        when(provider.fetchViews(anyLong(), anyCollection()))
                .thenReturn(Map.of("https://youtu.be/a", ViewCount.total(250L)));

        assertThat(service.syncApproved()).isEqualTo(1);

        assertThat(locked.getViews()).isEqualTo(250L);
        assertThat(locked.getCreditedKopecks()).isEqualTo(500_00L);
        assertThat(locked.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(stale.getViews()).isEqualTo(100L);
        verify(saverApplication).save(locked);
        verify(saverApplication, never()).save(stale);
        verify(saverViewSnapshot).save(any(ApplicationViewSnapshot.class));
        verify(campaignAccrualService).recalculate(campaign.getId());
    }

    @Test
    void syncSkipsApplicationDeletedBeforeLock() {
        Application gone = application(campaign(), "https://youtu.be/a", ApplicationStatus.APPROVED, 0L);
        when(getterApplication.getApproved()).thenReturn(List.of(gone));
        when(getterApplication.getByIdForUpdate(gone.getId())).thenReturn(Optional.empty());
        when(provider.fetchViews(anyLong(), anyCollection()))
                .thenReturn(Map.of("https://youtu.be/a", ViewCount.total(250L)));

        assertThat(service.syncApproved()).isZero();

        verify(saverViewSnapshot, never()).save(any());
        verify(saverApplication, never()).save(any());
        verify(campaignAccrualService, never()).recalculate(any(UUID.class));
    }

    @Test
    void syncRecalculatesRemainingCampaignsWhenOneConflicts() {
        Campaign busy = campaign();
        Campaign calm = campaign();
        Application first = application(busy, "https://youtu.be/a", ApplicationStatus.APPROVED, 0L);
        Application second = application(calm, "https://youtu.be/b", ApplicationStatus.APPROVED, 0L);
        when(getterApplication.getApproved()).thenReturn(List.of(first, second));
        when(getterApplication.getByIdForUpdate(first.getId())).thenReturn(Optional.of(first));
        when(getterApplication.getByIdForUpdate(second.getId())).thenReturn(Optional.of(second));
        when(provider.fetchViews(anyLong(), anyCollection())).thenReturn(Map.of(
                "https://youtu.be/a", ViewCount.total(250L),
                "https://youtu.be/b", ViewCount.total(300L)));
        doThrow(new ObjectOptimisticLockingFailureException(Campaign.class, busy.getId()))
                .when(campaignAccrualService).recalculate(busy.getId());

        assertThat(service.syncApproved()).isEqualTo(2);

        verify(campaignAccrualService).recalculate(calm.getId());
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
