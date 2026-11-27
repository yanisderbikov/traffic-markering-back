package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.placement.PlacementChoiceRequestDTO;
import ru.trafficmarkering.dto.placement.PlacementDecisionRequestDTO;
import ru.trafficmarkering.dto.placement.PlacementProofRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotSubmitRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.PayableViews;
import ru.trafficmarkering.model.application.Placement;
import ru.trafficmarkering.model.application.PlacementCheck;
import ru.trafficmarkering.model.application.PlacementKind;
import ru.trafficmarkering.model.application.PlacementState;
import ru.trafficmarkering.model.application.RateParts;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.application.PlacementService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.fraud.CreatorTrustService;
import ru.trafficmarkering.service.notify.ApplicationNotifications;
import ru.trafficmarkering.service.views.ViewsSyncService;
import ru.trafficmarkering.util.SnapshotPayout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Работы «ссылка и промокод» по принципу бота TopGift: ролик → свежий снимок просмотров → расчёт → подача
 * с подтверждением расчёта → модерация ролика и каждой надбавки отдельно → срок размещения → начисление.
 * Клиент не присылает ни просмотров, ни ставки, ни «закреплено»: только ссылки, вариант, доказательства
 * и номер расчёта, который он видел.
 */
@Log4j2
@Service
@RequiredArgsConstructor
class PlacementServiceImpl implements PlacementService {

    /** Сколько живёт расчёт: позже просмотры надо снять заново */
    static final Duration QUOTE_TTL = Duration.ofMinutes(15);

    /** Запас на расхождение часов площадки и сервера при проверке «ролик выложен после взятия оффера» */
    static final Duration PUBLISHED_TOLERANCE = Duration.ofMinutes(10);

    /** Автопроверка ссылки стоит кредит ScrapeCreators: чаще раза в минуту по одной работе не гоняем */
    static final Duration RECHECK_COOLDOWN = Duration.ofMinutes(1);

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final GetterCreatorProfile getterCreatorProfile;
    private final GetterViewSnapshot getterViewSnapshot;
    private final CurrentUserService currentUserService;
    private final CreatorTrustService creatorTrustService;
    private final FraudProperties fraudProperties;
    private final CommissionProperties commissionProperties;
    private final VideoChecks videoChecks;
    private final ProfileLinkChecker profileLinkChecker;
    private final PlacementDtos placementDtos;
    private final ViewsSyncService viewsSyncService;
    private final CampaignAccrualService campaignAccrualService;
    private final ApplicationNotifications notifications;
    private final PlacementFinalizer placementFinalizer;

    @Override
    @Transactional
    public ApplicationDTO choose(UUID applicationId, PlacementChoiceRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireOwn(applicationId, creator);
        requireSnapshotCampaign(application);
        requireInProgress(application);
        Placements.choose(application, application.getCampaign(), request.isWithLink(), request.isWithPromo());
        saverApplication.save(application);
        return forCreator(application);
    }

    @Override
    @Transactional
    public SnapshotQuoteDTO quote(UUID applicationId, SnapshotQuoteRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireOwn(applicationId, creator);
        Campaign campaign = requireSnapshotCampaign(application);
        requireInProgress(application);
        requireAcceptingWork(campaign);
        requireNotBlocked(creator);

        // Тематика ролика задаёт базовую ставку: выбор можно менять, пока работа не подана
        ContentTopics.choose(application, campaign, request.getContentTopic());
        String previousKey = application.getVideoKey();
        videoChecks.attach(application, campaign, creator, request.getVideoUrl().trim());
        if (!Objects.equals(previousKey, application.getVideoKey())) {
            // Другой ролик — счётчики и дата прежнего к нему не относятся
            application.setViews(0L);
            application.setCountryViews(null);
            application.setVideoPublishedAt(null);
            application.setViewsSyncedAt(null);
        }
        saverApplication.save(application);

        ApplicationViewSnapshot snapshot = viewsSyncService.captureNow(application.getId()).snapshot();
        requirePublishedAfterTaking(application);
        if (application.link().state().selected()) {
            profileLinkChecker.check(application);
        }
        saverApplication.save(application);
        return quoteOf(application, campaign, snapshot);
    }

    @Override
    @Transactional
    public ApplicationDTO submit(UUID applicationId, SnapshotSubmitRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireOwn(applicationId, creator);
        Campaign campaign = requireSnapshotCampaign(application);
        requireInProgress(application);
        requireAcceptingWork(campaign);
        requireNotBlocked(creator);
        if (application.getVideoUrl() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Сначала рассчитайте работу по ссылке на ролик");
        }
        if (campaign.hasContentRates() && campaign.contentRate(application.getContentTopic()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Выберите тематику ролика и рассчитайте работу ещё раз");
        }
        ApplicationViewSnapshot snapshot = requireFreshQuote(application, request);
        boolean withLink = application.link().state().selected();
        boolean withPromo = application.promo().state().selected();
        if (request.isWithLink() != withLink || request.isWithPromo() != withPromo) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Вариант участия изменился после расчёта — рассчитайте работу ещё раз");
        }

        Instant now = Instant.now();
        if (withLink) {
            submitLink(application, Placements.proofKey(request.getLinkProofKey()), now);
        }
        if (withPromo) {
            String proofKey = Placements.proofKey(request.getPromoProofKey());
            if (proofKey == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Приложите скриншот или запись экрана, где виден закреплённый комментарий с промокодом, "
                                + "или откажитесь от надбавки за промокод");
            }
            application.promo().resubmit(proofKey, Placements.proofUrl(request.getPromoCommentUrl()));
        }

        application.setBillingSnapshot(snapshot);
        application.setBillingViews(billableViews(application, campaign, snapshot));
        application.setSubmittedAt(now);
        application.setPlacementRetainUntil(now.plus(Duration.ofDays(campaign.retentionDays())));
        String comment = Placements.trimToNull(request.getComment());
        if (comment != null) {
            application.setComment(comment);
        }
        application.setStatus(ApplicationStatus.PENDING);
        saverApplication.save(application);
        notifications.submitted(application);
        return forCreator(application);
    }

    @Override
    @Transactional
    public ApplicationDTO resubmitProof(UUID applicationId, PlacementKind kind, PlacementProofRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireOwn(applicationId, creator);
        requireSnapshotCampaign(application);
        requireSubmittedAndAlive(application);
        Placement placement = requireRevisable(application, kind);
        String proofKey = Placements.proofKey(request.getProofKey());
        String proofUrl = Placements.proofUrl(request.getProofUrl());

        if (kind == PlacementKind.LINK && profileLinkChecker.check(application) == PlacementCheck.FOUND) {
            placement.resubmit(proofKey, proofUrl);
            placement.decide(PlacementState.VERIFIED, "Подтверждено автоматически: ссылка найдена в профиле",
                    null, Instant.now());
        } else {
            if (proofKey == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, kind == PlacementKind.LINK
                        ? "Ссылку в профиле автоматически не нашли — приложите скриншот профиля со ссылкой"
                        : "Приложите скриншот или запись экрана с закреплённым комментарием");
            }
            placement.resubmit(proofKey, proofUrl);
        }
        saverApplication.save(application);
        campaignAccrualService.recalculate(application.getCampaign());
        return forCreator(application);
    }

    @Override
    @Transactional
    public ApplicationDTO decline(UUID applicationId, PlacementKind kind) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireOwn(applicationId, creator);
        requireSnapshotCampaign(application);
        if (application.getStatus() == ApplicationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Работа ещё не подана — просто выберите другой вариант участия");
        }
        requireSubmittedAndAlive(application);
        Placement placement = requireRevisable(application, kind);
        placement.decide(PlacementState.NOT_SELECTED, "Креатор продолжил без этой надбавки", creator.getId(),
                Instant.now());
        saverApplication.save(application);
        campaignAccrualService.recalculate(application.getCampaign());
        return forCreator(application);
    }

    @Override
    @Transactional
    public ApplicationDTO decide(UUID applicationId, PlacementKind kind, PlacementDecisionRequestDTO request) {
        User moderator = currentUserService.require();
        Application application = requireApplication(applicationId);
        Campaign campaign = requireSnapshotCampaign(application);
        boolean owner = campaign.getCustomer() != null && Objects.equals(campaign.getCustomer().getId(), moderator.getId());
        if (!owner && !moderator.getRole().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Решать по надбавкам может заказчик объявления или модератор");
        }
        requireSubmittedAndAlive(application);
        PlacementState decision = request.getDecision();
        if (decision != PlacementState.VERIFIED && decision != PlacementState.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Надбавку можно только подтвердить или отклонить");
        }
        Placement placement = application.placement(kind);
        switch (placement.state()) {
            case NOT_SELECTED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Креатор не выбирал эту надбавку");
            case EARNED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Надбавка уже засчитана — решение окончательное");
            default -> {
            }
        }
        String reason = Placements.trimToNull(request.getReason());
        if (decision == PlacementState.REJECTED && reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину отказа: её увидит креатор");
        }
        Instant now = Instant.now();
        // Подтверждение после конца срока размещения сразу окончательно: держать ссылку дальше креатор не обязан
        boolean retentionOver = application.getPlacementRetainUntil() != null
                && !now.isBefore(application.getPlacementRetainUntil());
        PlacementState next = decision == PlacementState.VERIFIED && retentionOver ? PlacementState.EARNED : decision;
        placement.decide(next, reason, moderator.getId(), now);
        saverApplication.save(application);
        campaignAccrualService.recalculate(campaign);
        if (next == PlacementState.REJECTED) {
            notifications.placementRejected(application, kind);
        }
        return toDto(application, moderator);
    }

    @Override
    @Transactional
    public ApplicationDTO recheckLink(UUID applicationId) {
        User user = currentUserService.require();
        Application application = requireApplication(applicationId);
        Campaign campaign = requireSnapshotCampaign(application);
        boolean ownCreator = Objects.equals(application.getCreator().getId(), user.getId());
        boolean owner = campaign.getCustomer() != null && Objects.equals(campaign.getCustomer().getId(), user.getId());
        if (!ownCreator && !owner && !user.getRole().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        requireSubmittedAndAlive(application);
        Placement link = application.link();
        if (!link.state().selected()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Надбавка за ссылку в этой работе не выбрана");
        }
        if (link.getCheckedAt() != null && link.getCheckedAt().plus(RECHECK_COOLDOWN).isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Профиль проверяли меньше минуты назад — подождите немного");
        }
        PlacementCheck check = profileLinkChecker.check(application);
        // Автопроверка только помогает: найденная ссылка снимает ожидание, но отказ модератора не отменяет
        if (check == PlacementCheck.FOUND && link.state() == PlacementState.PENDING) {
            link.decide(PlacementState.VERIFIED, "Подтверждено автоматически: ссылка найдена в профиле", null,
                    Instant.now());
        }
        saverApplication.save(application);
        return toDto(application, user);
    }

    @Override
    public int finalizeDue() {
        List<UUID> due = getterApplication.getPlacementsDue(Instant.now()).stream()
                .map(Application::getId)
                .toList();
        int finalized = 0;
        for (UUID id : due) {
            try {
                if (placementFinalizer.finalizeOne(id)) {
                    finalized++;
                }
            } catch (RuntimeException e) {
                log.warn("Не удалось завершить срок размещения работы {}: {}", id, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Конец срока размещения: работ к обработке {}, обработано {}", due.size(), finalized);
        }
        return finalized;
    }

    // ---- расчёт ----

    private SnapshotQuoteDTO quoteOf(Application application, Campaign campaign, ApplicationViewSnapshot snapshot) {
        TrustLevel trust = creatorTrustService.levelOf(application.getCreator().getId());
        PayableViews payable = snapshot.payableViews(campaign.viewRegion());
        Long cap = trust.capsPayableViews() ? fraudProperties.getNewCreatorMaxPayableViews() : null;
        long billable = SnapshotPayout.billableViews(payable.views(), cap);
        boolean belowMin = !campaign.paysViews(billable);

        RateParts rates = application.rateParts();
        boolean withLink = application.link().state().selected();
        boolean withPromo = application.promo().state().selected();
        SnapshotPayout.Breakdown breakdown = SnapshotPayout.breakdown(billable, rates, withLink, withPromo);
        Long maxPerVideo = campaign.getMaxRewardPerVideoKopecks();
        long reward = belowMin ? 0L : SnapshotPayout.reward(billable, rates.rate(withLink, withPromo), maxPerVideo);
        long baseOnly = belowMin ? 0L : SnapshotPayout.reward(billable, rates.base(), maxPerVideo);
        long budgetLeft = campaign.remainingKopecks();
        long total = Math.min(reward, budgetLeft);
        long commission = commissionProperties.commissionOf(total);

        Placement link = application.link();
        Instant capturedAt = snapshot.getCapturedAt();
        return new SnapshotQuoteDTO(
                snapshot.getId(),
                capturedAt != null ? capturedAt.toString() : null,
                capturedAt != null ? capturedAt.plus(QUOTE_TTL).toString() : null,
                snapshot.getSource() != null ? snapshot.getSource().getDescription() : null,
                application.getVideoUrl(),
                application.getPlatform() != null ? application.getPlatform().getDescription() : null,
                application.getVideoPublishedAt() != null ? application.getVideoPublishedAt().toString() : null,
                snapshot.totalViews(),
                payable.views(),
                payable.geographyKnown(),
                billable,
                cap != null && payable.views() > cap,
                campaign.getMinPaidViews(),
                belowMin,
                rates.base(),
                rates.link(),
                rates.promo(),
                rates.rate(withLink, withPromo),
                withLink,
                withPromo,
                belowMin ? 0L : breakdown.base(),
                belowMin ? 0L : breakdown.link(),
                belowMin ? 0L : breakdown.promo(),
                belowMin ? 0L : breakdown.gross(),
                maxPerVideo,
                budgetLeft,
                !belowMin && total < breakdown.gross(),
                total,
                Math.min(baseOnly, budgetLeft),
                commissionProperties.getPercent(),
                commission,
                total - commission,
                withLink && link.getCheckState() != null ? link.getCheckState().name() : null,
                withLink && link.getCheckState() != null ? link.getCheckState().getDescription() : null,
                withLink ? link.getCheckDetail() : null,
                campaign.retentionDays(),
                Instant.now().plus(Duration.ofDays(campaign.retentionDays())).toString());
    }

    private long billableViews(Application application, Campaign campaign, ApplicationViewSnapshot snapshot) {
        TrustLevel trust = creatorTrustService.levelOf(application.getCreator().getId());
        Long cap = trust.capsPayableViews() ? fraudProperties.getNewCreatorMaxPayableViews() : null;
        return SnapshotPayout.billableViews(snapshot.payableViews(campaign.viewRegion()).views(), cap);
    }

    /** Ссылка при подаче: нашли в профиле — подтверждена сразу, иначе нужен скриншот и решение модератора. */
    private void submitLink(Application application, String proofKey, Instant now) {
        Placement link = application.link();
        PlacementCheck check = link.getCheckState() != null ? link.getCheckState() : profileLinkChecker.check(application);
        if (check == PlacementCheck.FOUND) {
            link.resubmit(proofKey, null);
            link.decide(PlacementState.VERIFIED, "Подтверждено автоматически: ссылка найдена в профиле", null, now);
            return;
        }
        if (proofKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылку в профиле автоматически не подтвердили — приложите скриншот профиля со ссылкой "
                            + "или откажитесь от надбавки за ссылку");
        }
        link.resubmit(proofKey, null);
    }

    /** Подтверждается ровно тот расчёт, что видел креатор, и пока он свежий. */
    private ApplicationViewSnapshot requireFreshQuote(Application application, SnapshotSubmitRequestDTO request) {
        List<ApplicationViewSnapshot> snapshots = getterViewSnapshot.getByApplicationId(application.getId());
        ApplicationViewSnapshot latest = snapshots.isEmpty() ? null : snapshots.get(0);
        if (latest == null || !Objects.equals(latest.getId(), request.getSnapshotId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Расчёт устарел: просмотры уже снимались заново. Рассчитайте работу ещё раз");
        }
        if (latest.getCapturedAt() == null || latest.getCapturedAt().plus(QUOTE_TTL).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Расчёт старше " + QUOTE_TTL.toMinutes() + " минут — рассчитайте ещё раз, чтобы зафиксировать "
                            + "свежие просмотры");
        }
        return latest;
    }

    /** Старые просмотры за рекламу, добавленную задним числом, не оплачиваем: ролик должен быть новым. */
    private void requirePublishedAfterTaking(Application application) {
        Instant published = application.getVideoPublishedAt();
        Instant taken = application.getCreatedAt();
        if (published != null && taken != null && published.isBefore(taken.minus(PUBLISHED_TOLERANCE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ролик опубликован раньше, чем вы взяли оффер: по этому объявлению принимаются только новые "
                            + "рекламные ролики");
        }
    }

    // ---- доступы и состояния ----

    private Application requireApplication(UUID id) {
        return getterApplication.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отклик не найден: " + id));
    }

    private Application requireOwn(UUID id, User creator) {
        Application application = requireApplication(id);
        if (!creator.getRole().isAdmin() && !Objects.equals(application.getCreator().getId(), creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        return application;
    }

    private static Campaign requireSnapshotCampaign(Application application) {
        Campaign campaign = application.getCampaign();
        if (!campaign.paysBySnapshot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "У этого объявления нет надбавок за ссылку и промокод");
        }
        return campaign;
    }

    private static void requireInProgress(Application application) {
        if (application.getStatus() != ApplicationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Работа уже подана — расчёт зафиксирован");
        }
    }

    private static void requireSubmittedAndAlive(Application application) {
        if (application.getSubmittedAt() == null || application.getStatus() == ApplicationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Работа ещё не подана");
        }
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ролик отклонён — надбавки к нему не начисляются");
        }
    }

    private static Placement requireRevisable(Application application, PlacementKind kind) {
        Placement placement = application.placement(kind);
        PlacementState state = placement.state();
        if (state == PlacementState.NOT_SELECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта надбавка в работе не выбрана");
        }
        if (!state.revisable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Надбавка уже подтверждена — исправлять ничего не нужно");
        }
        return placement;
    }

    private static void requireAcceptingWork(Campaign campaign) {
        Instant now = Instant.now();
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Объявление не принимает работы: " + campaign.getStatus().getDescription().toLowerCase());
        }
        if (campaign.endedBy(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Срок действия объявления истёк");
        }
    }

    private void requireNotBlocked(User creator) {
        if (creatorTrustService.levelOf(creator.getId()).blocksApplications()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Аккаунт заблокирован за накрутку просмотров: подавать работы нельзя");
        }
    }

    private ApplicationDTO forCreator(Application application) {
        return dto(application).forCreator();
    }

    private ApplicationDTO toDto(Application application, User viewer) {
        boolean ownCreator = Objects.equals(application.getCreator().getId(), viewer.getId());
        ApplicationDTO dto = dto(application);
        return ownCreator && !viewer.getRole().isAdmin() ? dto.forCreator() : dto;
    }

    private ApplicationDTO dto(Application application) {
        return placementDtos.attach(ApplicationDTO.from(application,
                getterCreatorProfile.getByUserId(application.getCreator().getId()).orElse(null)), application);
    }
}
