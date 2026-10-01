package ru.trafficmarkering.service.application.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationVideoRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
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
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.service.fraud.CreatorTrustService;
import ru.trafficmarkering.service.fraud.FraudCheckService;
import ru.trafficmarkering.service.http.ShortLinkResolver;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private final CreatorTrustService creatorTrustService = mock(CreatorTrustService.class);
    private final FraudCheckService fraudCheckService = mock(FraudCheckService.class);

    private final ApplicationServiceImpl service = new ApplicationServiceImpl(
            getterApplication, saverApplication, applicationDeleter, getterCampaign, getterCreatorProfile,
            getterSocialAccount, currentUserService, campaignAccrualService, getterViewSnapshot,
            saverViewSnapshot, shortLinkResolver, creatorTrustService, fraudCheckService);

    {
        when(creatorTrustService.levelOf(any())).thenReturn(TrustLevel.NEW);
    }

    @Test
    void apply_rejectsBlockedCreator() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(creatorTrustService.levelOf(creator.getId())).thenReturn(TrustLevel.BLOCKED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.apply(request(campaign, YOUTUBE_URL)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(saverApplication, never()).save(any());
    }

    private final User customer = User.builder().id(1L).name("Заказчик").role(Role.CUSTOMER).build();
    private final User creator = User.builder().id(2L).name("Криатор").role(Role.CREATOR).build();
    private final User manager = User.builder().id(3L).name("Менеджер").role(Role.ADMIN).build();

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

    @Test
    void apply_withoutVideoTakesOfferInProgress() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(getterApplication.getInProgress(campaign.getId(), creator.getId())).thenReturn(Optional.empty());
        when(getterApplication.existsByPublicId(anyString())).thenReturn(false);
        when(getterCreatorProfile.getByUserId(creator.getId())).thenReturn(Optional.empty());
        when(saverApplication.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationDTO saved = service.apply(request(campaign, null));

        assertEquals("IN_PROGRESS", saved.status());
        assertNull(saved.videoUrl());
        assertNull(saved.platform());
        verify(getterSocialAccount, never()).getActiveByUserIdAndPlatform(any(), any());
    }

    @Test
    void apply_withoutVideoReturnsExistingWorkInProgress() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application existing = inProgress(campaign);
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(getterApplication.getInProgress(campaign.getId(), creator.getId())).thenReturn(Optional.of(existing));
        when(getterCreatorProfile.getByUserId(creator.getId())).thenReturn(Optional.empty());

        ApplicationDTO result = service.apply(request(campaign, "  "));

        assertEquals(existing.getId(), result.id());
        verify(saverApplication, never()).save(any());
    }

    @Test
    void attachVideo_sendsWorkToReview() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = inProgress(campaign);
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));
        when(getterSocialAccount.getActiveByUserIdAndPlatform(creator.getId(), Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.of(new SocialAccount()));
        when(getterApplication.getActiveByVideoKey(anyString())).thenReturn(Optional.empty());
        when(getterCreatorProfile.getByUserId(creator.getId())).thenReturn(Optional.empty());

        ApplicationDTO result = service.attachVideo(application.getId(),
                ApplicationVideoRequestDTO.builder().videoUrl(YOUTUBE_URL).comment(" Готово ").build());

        assertEquals("PENDING", result.status());
        assertEquals("YOUTUBE_SHORTS", result.platform());
        assertEquals(YOUTUBE_URL, result.videoUrl());
        assertEquals("Готово", result.comment());
        verify(saverApplication).save(application);
    }

    @Test
    void attachVideo_rejectsWorkAlreadyWithVideo() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = inProgress(campaign);
        application.setStatus(ApplicationStatus.PENDING);
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.attachVideo(application.getId(),
                        ApplicationVideoRequestDTO.builder().videoUrl(YOUTUBE_URL).build()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(saverApplication, never()).save(any());
    }

    @Test
    void updateStatus_rejectsWorkWithoutVideo() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = inProgress(campaign);
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(application.getId(),
                        new ApplicationStatusUpdateRequestDTO(ApplicationStatus.APPROVED, null)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(saverApplication, never()).save(any());
    }

    @Test
    void moderate_approvesPendingVideoAndStartsAccrual() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = pending(campaign);
        when(currentUserService.require(Role.ADMIN)).thenReturn(manager);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ApplicationDTO moderated = service.moderate(application.getId(),
                new ApplicationStatusUpdateRequestDTO(ApplicationStatus.APPROVED, null));

        assertEquals("APPROVED", moderated.status());
        assertEquals(manager, application.getModeratedBy());
        assertTrue(application.isAccruable());
        verify(campaignAccrualService).recalculate(campaign);
    }

    @Test
    void moderate_requiresRejectionReason() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = pending(campaign);
        when(currentUserService.require(Role.ADMIN)).thenReturn(manager);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.moderate(application.getId(),
                        new ApplicationStatusUpdateRequestDTO(ApplicationStatus.REJECTED, "  ")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(ApplicationStatus.PENDING, application.getStatus());
        verify(saverApplication, never()).save(any());
    }

    @Test
    void moderate_keepsRejectionReasonForCreator() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = pending(campaign);
        when(currentUserService.require(Role.ADMIN)).thenReturn(manager);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ApplicationDTO moderated = service.moderate(application.getId(),
                new ApplicationStatusUpdateRequestDTO(ApplicationStatus.REJECTED, " Ролик не по брифу "));

        assertEquals("REJECTED", moderated.status());
        assertEquals("Ролик не по брифу", moderated.rejectionReason());
    }

    @Test
    void moderate_refusesAlreadyDecidedApplication() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = pending(campaign);
        application.setStatus(ApplicationStatus.APPROVED);
        when(currentUserService.require(Role.ADMIN)).thenReturn(manager);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.moderate(application.getId(),
                        new ApplicationStatusUpdateRequestDTO(ApplicationStatus.REJECTED, "Поздно")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(saverApplication, never()).save(any());
    }

    @Test
    void updateStatus_customerRejectionNeedsReason() {
        Campaign campaign = activeCampaign(EnumSet.of(Platform.YOUTUBE_SHORTS));
        Application application = pending(campaign);
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        when(getterApplication.getById(application.getId())).thenReturn(Optional.of(application));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(application.getId(),
                        new ApplicationStatusUpdateRequestDTO(ApplicationStatus.REJECTED, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(saverApplication, never()).save(any());
    }

    private Application pending(Campaign campaign) {
        Application application = inProgress(campaign);
        application.setPlatform(Platform.YOUTUBE_SHORTS);
        application.setVideoUrl(YOUTUBE_URL);
        application.setStatus(ApplicationStatus.PENDING);
        return application;
    }

    private Application inProgress(Campaign campaign) {
        return Application.builder()
                .id(UUID.randomUUID())
                .publicId("APP00001")
                .campaign(campaign)
                .creator(creator)
                .status(ApplicationStatus.IN_PROGRESS)
                .build();
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
