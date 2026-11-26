package ru.trafficmarkering.service.application.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.dto.placement.PlacementDecisionRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotSubmitRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.Placement;
import ru.trafficmarkering.model.application.PlacementCheck;
import ru.trafficmarkering.model.application.PlacementKind;
import ru.trafficmarkering.model.application.PlacementState;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.campaign.PaymentModel;
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.fraud.CreatorTrustService;
import ru.trafficmarkering.service.notify.ApplicationNotifications;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.views.CapturedViews;
import ru.trafficmarkering.service.views.ViewsSyncService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacementServiceImplTest {

    private static final String VIDEO = "https://www.youtube.com/shorts/dQw4w9WgXcQ";

    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final SaverApplication saverApplication = mock(SaverApplication.class);
    private final GetterCreatorProfile getterCreatorProfile = mock(GetterCreatorProfile.class);
    private final GetterViewSnapshot getterViewSnapshot = mock(GetterViewSnapshot.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final CreatorTrustService creatorTrustService = mock(CreatorTrustService.class);
    private final VideoChecks videoChecks = mock(VideoChecks.class);
    private final ProfileLinkChecker profileLinkChecker = mock(ProfileLinkChecker.class);
    private final ViewsSyncService viewsSyncService = mock(ViewsSyncService.class);
    private final CampaignAccrualService campaignAccrualService = mock(CampaignAccrualService.class);
    private final ApplicationNotifications notifications = mock(ApplicationNotifications.class);
    private final PlacementFinalizer placementFinalizer = mock(PlacementFinalizer.class);

    private final PlacementServiceImpl service = new PlacementServiceImpl(getterApplication, saverApplication,
            getterCreatorProfile, getterViewSnapshot, currentUserService, creatorTrustService, new FraudProperties(),
            new CommissionProperties(), videoChecks, profileLinkChecker, new PlacementDtos(mock(FileStorage.class)),
            viewsSyncService, campaignAccrualService, notifications, placementFinalizer);

    private final User customer = User.builder().id(1L).name("Заказчик").role(Role.CUSTOMER).build();
    private final User creator = User.builder().id(2L).name("Криатор").role(Role.CREATOR).build();
    private final User stranger = User.builder().id(9L).name("Чужой").role(Role.CUSTOMER).build();

    private Campaign campaign;
    private Application work;

    @BeforeEach
    void setUp() {
        campaign = Campaign.builder()
                .id(UUID.randomUUID())
                .title("VPN")
                .customer(customer)
                .status(CampaignStatus.ACTIVE)
                .paymentModel(PaymentModel.SNAPSHOT)
                .ratePerThousandKopecks(50_00L)
                .linkBonusPerThousandKopecks(30_00L)
                .promoBonusPerThousandKopecks(20_00L)
                .promoUrl("https://example.com/app")
                .promoCode("OFFER20")
                .budgetKopecks(100_000_00L)
                .spentKopecks(0L)
                .build();
        work = Application.builder()
                .id(UUID.randomUUID())
                .campaign(campaign)
                .creator(creator)
                .status(ApplicationStatus.IN_PROGRESS)
                .baseRateKopecks(50_00L)
                .linkBonusKopecks(30_00L)
                .promoBonusKopecks(20_00L)
                .createdAt(Instant.now().minus(Duration.ofDays(1)))
                .linkPlacement(Placement.of(true))
                .promoPlacement(Placement.of(true))
                .build();
        when(getterApplication.getById(work.getId())).thenReturn(Optional.of(work));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(creatorTrustService.levelOf(any())).thenReturn(TrustLevel.TRUSTED);
        when(getterCreatorProfile.getByUserId(any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            application.setPlatform(Platform.YOUTUBE_SHORTS);
            application.setVideoUrl(VIDEO);
            application.setVideoKey("YOUTUBE_SHORTS:dQw4w9WgXcQ");
            return null;
        }).when(videoChecks).attach(any(), any(), any(), any());
    }

    @Test
    void quote_freshSnapshotWithBreakdownAndLinkCheck() {
        ApplicationViewSnapshot snapshot = capture(10_480L, Instant.now());
        linkCheckReturns(PlacementCheck.FOUND);

        SnapshotQuoteDTO quote = service.quote(work.getId(), new SnapshotQuoteRequestDTO(VIDEO, null));

        assertEquals(snapshot.getId(), quote.snapshotId());
        assertEquals(10_400L, quote.billableViews());
        assertEquals(520_00L, quote.baseKopecks());
        assertEquals(312_00L, quote.linkKopecks());
        assertEquals(208_00L, quote.promoKopecks());
        assertEquals(1_040_00L, quote.totalKopecks());
        assertEquals(520_00L, quote.baseOnlyKopecks());
        assertEquals(104_00L, quote.commissionKopecks());
        assertEquals(936_00L, quote.netKopecks());
        assertEquals("FOUND", quote.linkCheckState());
        assertEquals(ApplicationStatus.IN_PROGRESS, work.getStatus());
    }

    @Test
    void quote_rejectsVideoPublishedBeforeTakingOffer() {
        when(viewsSyncService.captureNow(work.getId())).thenAnswer(invocation -> {
            work.setVideoPublishedAt(work.getCreatedAt().minus(Duration.ofDays(3)));
            return new CapturedViews(snapshot(5_000L, Instant.now()));
        });

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.quote(work.getId(), new SnapshotQuoteRequestDTO(VIDEO, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertThat(error.getReason()).contains("только новые");
    }

    @Test
    void submit_freezesSnapshotAndVerifiesFoundLinkAutomatically() {
        ApplicationViewSnapshot snapshot = quoted(10_480L, PlacementCheck.FOUND);

        service.submit(work.getId(), submit(snapshot, true, true, null, "placement-proofs/pin.mp4"));

        assertEquals(ApplicationStatus.PENDING, work.getStatus());
        assertEquals(10_400L, work.getBillingViews());
        assertEquals(snapshot, work.getBillingSnapshot());
        assertEquals(PlacementState.VERIFIED, work.link().state());
        assertEquals(PlacementState.PENDING, work.promo().state());
        assertEquals("placement-proofs/pin.mp4", work.promo().getProofKey());
        assertThat(work.getPlacementRetainUntil()).isBetween(
                Instant.now().plus(Duration.ofDays(7)).minusSeconds(60), Instant.now().plus(Duration.ofDays(7)));
        verify(notifications).submitted(work);
    }

    @Test
    void submit_requiresProofWhenLinkNotFoundAndForPromo() {
        ApplicationViewSnapshot snapshot = quoted(10_000L, PlacementCheck.UNKNOWN);

        ResponseStatusException noLinkProof = assertThrows(ResponseStatusException.class,
                () -> service.submit(work.getId(), submit(snapshot, true, true, null, "placement-proofs/pin.mp4")));
        assertEquals(HttpStatus.BAD_REQUEST, noLinkProof.getStatusCode());

        ResponseStatusException noPromoProof = assertThrows(ResponseStatusException.class,
                () -> service.submit(work.getId(), submit(snapshot, true, true, "placement-proofs/bio.png", null)));
        assertEquals(HttpStatus.BAD_REQUEST, noPromoProof.getStatusCode());
        assertThat(noPromoProof.getReason()).contains("закреплённый комментарий");
        verify(notifications, never()).submitted(any());
    }

    @Test
    void submit_rejectsStaleOrForeignQuote() {
        ApplicationViewSnapshot old = snapshot(10_000L, Instant.now().minus(Duration.ofMinutes(20)));
        when(getterViewSnapshot.getByApplicationId(work.getId())).thenReturn(List.of(old));
        work.setVideoUrl(VIDEO);

        ResponseStatusException stale = assertThrows(ResponseStatusException.class,
                () -> service.submit(work.getId(), submit(old, true, true, "placement-proofs/a.png", "placement-proofs/b.png")));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());

        ApplicationViewSnapshot fresh = snapshot(10_000L, Instant.now());
        when(getterViewSnapshot.getByApplicationId(work.getId())).thenReturn(List.of(fresh, old));
        ResponseStatusException replaced = assertThrows(ResponseStatusException.class,
                () -> service.submit(work.getId(), submit(old, true, true, "placement-proofs/a.png", "placement-proofs/b.png")));
        assertEquals(HttpStatus.CONFLICT, replaced.getStatusCode());
    }

    @Test
    void submit_rejectsChangedVariantAndForeignProofKeys() {
        ApplicationViewSnapshot snapshot = quoted(10_000L, PlacementCheck.FOUND);

        ResponseStatusException changed = assertThrows(ResponseStatusException.class,
                () -> service.submit(work.getId(), submit(snapshot, false, true, null, "placement-proofs/pin.mp4")));
        assertEquals(HttpStatus.CONFLICT, changed.getStatusCode());

        ResponseStatusException foreignKey = assertThrows(ResponseStatusException.class,
                () -> service.submit(work.getId(), submit(snapshot, true, true, null, "campaign-photos/x.png")));
        assertEquals(HttpStatus.BAD_REQUEST, foreignKey.getStatusCode());
    }

    @Test
    void decide_rejectNeedsReasonAndNotifiesCreator() {
        submittedWork();
        when(currentUserService.require()).thenReturn(customer);

        ResponseStatusException noReason = assertThrows(ResponseStatusException.class, () -> service.decide(work.getId(),
                PlacementKind.PROMO, new PlacementDecisionRequestDTO(PlacementState.REJECTED, " ")));
        assertEquals(HttpStatus.BAD_REQUEST, noReason.getStatusCode());

        service.decide(work.getId(), PlacementKind.PROMO,
                new PlacementDecisionRequestDTO(PlacementState.REJECTED, "Комментарий не закреплён"));

        assertEquals(PlacementState.REJECTED, work.promo().state());
        assertEquals("Комментарий не закреплён", work.promo().getNote());
        assertEquals(customer.getId(), work.promo().getDecidedBy());
        assertEquals(ApplicationStatus.APPROVED, work.getStatus());
        verify(notifications).placementRejected(work, PlacementKind.PROMO);
        verify(campaignAccrualService).recalculate(campaign);
    }

    @Test
    void decide_onlyOwnerOrModerator_earnedIsFinal_lateVerifyEarnsAtOnce() {
        submittedWork();
        when(currentUserService.require()).thenReturn(stranger);
        ResponseStatusException foreign = assertThrows(ResponseStatusException.class, () -> service.decide(work.getId(),
                PlacementKind.PROMO, new PlacementDecisionRequestDTO(PlacementState.VERIFIED, null)));
        assertEquals(HttpStatus.FORBIDDEN, foreign.getStatusCode());

        when(currentUserService.require()).thenReturn(customer);
        work.setPlacementRetainUntil(Instant.now().minus(Duration.ofHours(1)));
        service.decide(work.getId(), PlacementKind.PROMO, new PlacementDecisionRequestDTO(PlacementState.VERIFIED, null));
        assertEquals(PlacementState.EARNED, work.promo().state());

        ResponseStatusException finalDecision = assertThrows(ResponseStatusException.class, () -> service.decide(
                work.getId(), PlacementKind.PROMO, new PlacementDecisionRequestDTO(PlacementState.REJECTED, "передумал")));
        assertEquals(HttpStatus.CONFLICT, finalDecision.getStatusCode());
    }

    @Test
    void decline_dropsOnlyThatBonus() {
        submittedWork();
        work.promo().setState(PlacementState.REJECTED);

        service.decline(work.getId(), PlacementKind.PROMO);

        assertEquals(PlacementState.NOT_SELECTED, work.promo().state());
        assertEquals(PlacementState.VERIFIED, work.link().state());
        verify(campaignAccrualService).recalculate(campaign);

        ResponseStatusException verified = assertThrows(ResponseStatusException.class,
                () -> service.decline(work.getId(), PlacementKind.LINK));
        assertEquals(HttpStatus.CONFLICT, verified.getStatusCode());
    }

    @Test
    void finalizer_linkMissingAtEndGoesBackToReview_promoEarned() {
        submittedWork();
        work.promo().setState(PlacementState.VERIFIED);
        work.setPlacementRetainUntil(Instant.now().minus(Duration.ofMinutes(5)));
        when(getterApplication.getByIdForUpdate(work.getId())).thenReturn(Optional.of(work));
        linkCheckReturns(PlacementCheck.MISSING);
        PlacementFinalizer finalizer = new PlacementFinalizer(getterApplication, saverApplication, profileLinkChecker,
                notifications);

        assertThat(finalizer.finalizeOne(work.getId())).isTrue();

        assertEquals(PlacementState.PENDING, work.link().state());
        assertThat(work.link().getNote()).contains("не нашли");
        assertEquals(PlacementState.EARNED, work.promo().state());
        verify(notifications).linkMissingAtRetentionEnd(work);
    }

    @Test
    void finalizer_unknownSourceIsNotAPenalty() {
        submittedWork();
        work.setPlacementRetainUntil(Instant.now().minus(Duration.ofMinutes(5)));
        when(getterApplication.getByIdForUpdate(work.getId())).thenReturn(Optional.of(work));
        linkCheckReturns(PlacementCheck.UNKNOWN);
        PlacementFinalizer finalizer = new PlacementFinalizer(getterApplication, saverApplication, profileLinkChecker,
                notifications);

        assertThat(finalizer.finalizeOne(work.getId())).isTrue();

        assertEquals(PlacementState.EARNED, work.link().state());
        verify(notifications, never()).linkMissingAtRetentionEnd(any());
    }

    // ---- помощники ----

    private void submittedWork() {
        work.setStatus(ApplicationStatus.APPROVED);
        work.setPlatform(Platform.YOUTUBE_SHORTS);
        work.setVideoUrl(VIDEO);
        work.setSubmittedAt(Instant.now().minus(Duration.ofDays(1)));
        work.setBillingViews(10_000L);
        work.setPlacementRetainUntil(Instant.now().plus(Duration.ofDays(6)));
        work.link().setState(PlacementState.VERIFIED);
        work.promo().setState(PlacementState.PENDING);
    }

    private ApplicationViewSnapshot quoted(long views, PlacementCheck linkCheck) {
        ApplicationViewSnapshot snapshot = capture(views, Instant.now());
        linkCheckReturns(linkCheck);
        service.quote(work.getId(), new SnapshotQuoteRequestDTO(VIDEO, null));
        when(getterViewSnapshot.getByApplicationId(work.getId())).thenReturn(List.of(snapshot));
        return snapshot;
    }

    private ApplicationViewSnapshot capture(long views, Instant capturedAt) {
        ApplicationViewSnapshot snapshot = snapshot(views, capturedAt);
        when(viewsSyncService.captureNow(work.getId())).thenReturn(new CapturedViews(snapshot));
        return snapshot;
    }

    private long nextSnapshotId = 40;

    private ApplicationViewSnapshot snapshot(long views, Instant capturedAt) {
        return ApplicationViewSnapshot.builder()
                .id(nextSnapshotId++)
                .application(work)
                .capturedAt(capturedAt)
                .views(views)
                .source(ViewSource.SCRAPE_CREATORS)
                .build();
    }

    private void linkCheckReturns(PlacementCheck check) {
        when(profileLinkChecker.check(any())).thenAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            application.link().recordCheck(check, "проверка", Instant.now());
            return check;
        });
    }

    private static SnapshotSubmitRequestDTO submit(ApplicationViewSnapshot snapshot, boolean withLink, boolean withPromo,
                                                   String linkProofKey, String promoProofKey) {
        return SnapshotSubmitRequestDTO.builder()
                .snapshotId(snapshot.getId())
                .withLink(withLink)
                .withPromo(withPromo)
                .linkProofKey(linkProofKey)
                .promoProofKey(promoProofKey)
                .build();
    }
}
