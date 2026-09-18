package ru.trafficmarkering.service.application.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.ApplicationDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.http.ShortLinkResolver;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceImplTest {

    private static final String YOUTUBE_URL = "https://www.youtube.com/shorts/dQw4w9WgXcQ";

    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final SaverApplication saverApplication = mock(SaverApplication.class);
    private final ApplicationDeleter applicationDeleter = mock(ApplicationDeleter.class);
    private final GetterCampaign getterCampaign = mock(GetterCampaign.class);
    private final GetterCreatorProfile getterCreatorProfile = mock(GetterCreatorProfile.class);
    private final GetterSocialAccount getterSocialAccount = mock(GetterSocialAccount.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final CampaignAccrualService campaignAccrualService = mock(CampaignAccrualService.class);
    private final GetterViewSnapshot getterViewSnapshot = mock(GetterViewSnapshot.class);
    private final SaverViewSnapshot saverViewSnapshot = mock(SaverViewSnapshot.class);
    private final ShortLinkResolver shortLinkResolver = new ShortLinkResolver();

    private final ApplicationServiceImpl service = new ApplicationServiceImpl(
            getterApplication, saverApplication, applicationDeleter, getterCampaign, getterCreatorProfile,
            getterSocialAccount, currentUserService, campaignAccrualService, getterViewSnapshot,
            saverViewSnapshot, shortLinkResolver);

    private final User customer = User.builder().id(1L).name("Заказчик").role(Role.CUSTOMER).build();
    private final User creator = User.builder().id(2L).name("Криатор").role(Role.CREATOR).build();

    @Test
    void apply_rejectsVideoFromPlatformCampaignDoesNotAccept() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.TIKTOK, Platform.INSTAGRAM));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.apply(request(campaign, YOUTUBE_URL)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("YouTube Shorts"));
        assertTrue(error.getReason().contains("Instagram, TikTok"));
        verify(saverApplication, never()).save(any());
        verify(getterSocialAccount, never()).getActiveByUserIdAndPlatform(any(), any());
    }

    @Test
    void apply_acceptsVideoFromCampaignPlatform() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(getterSocialAccount.getActiveByUserIdAndPlatform(creator.getId(), Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.of(new SocialAccount()));
        when(getterApplication.getActiveByVideoKey(anyString())).thenReturn(Optional.empty());
        when(getterApplication.existsByPublicId(anyString())).thenReturn(false);
        when(getterCreatorProfile.getByUserId(creator.getId())).thenReturn(Optional.empty());
        when(saverApplication.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationDTO saved = service.apply(request(campaign, YOUTUBE_URL));

        assertEquals("YOUTUBE_SHORTS", saved.platform());
        assertEquals(campaign.getId(), saved.campaignId());
        verify(saverApplication).save(any());
    }

    @Test
    void apply_rejectsWhenOfferHasNotStarted() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        campaign.setStartsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.apply(request(campaign, YOUTUBE_URL)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("начнёт принимать отклики"));
        verify(saverApplication, never()).save(any());
    }

    @Test
    void apply_rejectsWhenOfferHasEnded() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        campaign.setEndsAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.apply(request(campaign, YOUTUBE_URL)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("истёк"));
        verify(saverApplication, never()).save(any());
    }

    @Test
    void apply_rejectsWhenCreatorReachedVideoLimit() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        campaign.setMaxVideosPerCreator(2);
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(getterApplication.countActiveByCampaignIdAndCreatorId(campaign.getId(), creator.getId())).thenReturn(2L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.apply(request(campaign, YOUTUBE_URL)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("не больше 2 роликов"));
        verify(saverApplication, never()).save(any());
    }

    @Test
    void apply_acceptsWithinPeriodAndUnderVideoLimit() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        campaign.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        campaign.setEndsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        campaign.setMaxVideosPerCreator(2);
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(getterApplication.countActiveByCampaignIdAndCreatorId(campaign.getId(), creator.getId())).thenReturn(1L);
        when(getterSocialAccount.getActiveByUserIdAndPlatform(creator.getId(), Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.of(new SocialAccount()));
        when(getterApplication.getActiveByVideoKey(anyString())).thenReturn(Optional.empty());
        when(getterApplication.existsByPublicId(anyString())).thenReturn(false);
        when(getterCreatorProfile.getByUserId(creator.getId())).thenReturn(Optional.empty());
        when(saverApplication.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationDTO saved = service.apply(request(campaign, YOUTUBE_URL));

        assertEquals(campaign.getId(), saved.campaignId());
        verify(saverApplication).save(any());
    }

    private Campaign activeCampaign(Set<Platform> platforms) {
        return Campaign.builder()
                .id(UUID.randomUUID())
                .publicId("CAMP0001")
                .customer(customer)
                .title("Интеграция")
                .description("Снять ролик")
                .ratePerThousandKopecks(350_00L)
                .budgetKopecks(10_000_00L)
                .status(CampaignStatus.ACTIVE)
                .platforms(platforms)
                .build();
    }

    private ApplicationCreateRequestDTO request(Campaign campaign, String videoUrl) {
        return ApplicationCreateRequestDTO.builder()
                .campaignId(campaign.getId())
                .videoUrl(videoUrl)
                .build();
    }
}
