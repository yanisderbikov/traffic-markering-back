package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationVideoRequestDTO;
import ru.trafficmarkering.dto.application.ViewSnapshotDTO;
import ru.trafficmarkering.dto.application.ViewsUpdateRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.CountryViewsConverter;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.ApplicationDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.application.ApplicationService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.fraud.CreatorTrustService;
import ru.trafficmarkering.service.fraud.FraudCheckService;
import ru.trafficmarkering.service.http.ShortLinkResolver;
import ru.trafficmarkering.util.PublicIdGenerator;
import ru.trafficmarkering.util.VideoUrls;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Отклики криаторов. Здесь же живут все проверки «кому что можно»: криатор распоряжается
 * своим откликом, заказчик — откликами на свои объявления.
 * Методы транзакционные: связи отклика (объявление, криатор) ленивые, а DTO собирается
 * из них же — вне транзакции маппинг падал бы на LazyInitializationException.
 */
@Service
@RequiredArgsConstructor
class ApplicationServiceImpl implements ApplicationService {

    /** Что заказчику разрешено выставить: PENDING отклик получает при создании и обратно не возвращается */
    private static final Set<ApplicationStatus> CUSTOMER_DECISIONS =
            EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED, ApplicationStatus.COMPLETED);

    private static final Set<ApplicationStatus> MODERATION_DECISIONS =
            EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final ApplicationDeleter applicationDeleter;
    private final GetterCampaign getterCampaign;
    private final GetterCreatorProfile getterCreatorProfile;
    private final GetterSocialAccount getterSocialAccount;
    private final CurrentUserService currentUserService;
    private final CampaignAccrualService campaignAccrualService;
    private final GetterViewSnapshot getterViewSnapshot;
    private final SaverViewSnapshot saverViewSnapshot;
    private final ShortLinkResolver shortLinkResolver;
    private final CreatorTrustService creatorTrustService;
    private final FraudCheckService fraudCheckService;

    @Override
    @Transactional
    public ApplicationDTO apply(ApplicationCreateRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Campaign campaign = getterCampaign.getById(request.getCampaignId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Объявление не найдено: " + request.getCampaignId()));

        requireAcceptingApplications(campaign);
        // ADMIN проходит проверку роли выше, поэтому теоретически может оказаться и заказчиком
        if (Objects.equals(campaign.getCustomer().getId(), creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Нельзя откликнуться на собственное объявление");
        }
        requireNotBlocked(creator);

        String rawVideoUrl = trimToNull(request.getVideoUrl());
        if (rawVideoUrl == null) {
            Optional<Application> inProgress = getterApplication.getInProgress(campaign.getId(), creator.getId());
            if (inProgress.isPresent()) {
                return ApplicationDTO.from(inProgress.get(), campaign, creator, creatorProfile(creator.getId()));
            }
        }
        requireVideoLimitNotReached(campaign, creator);

        Application application = Application.builder()
                .publicId(PublicIdGenerator.generateUnique(getterApplication::existsByPublicId))
                .campaign(campaign)
                .creator(creator)
                .comment(trimToNull(request.getComment()))
                .status(ApplicationStatus.IN_PROGRESS)
                .build();
        if (rawVideoUrl != null) {
            submitVideo(application, campaign, creator, rawVideoUrl);
        }

        Application saved = saverApplication.save(application);
        return ApplicationDTO.from(saved, campaign, creator, creatorProfile(creator.getId()));
    }

    @Override
    @Transactional
    public ApplicationDTO attachVideo(UUID id, ApplicationVideoRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireApplication(id);
        if (isForeign(application.getCreator().getId(), creator)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        if (application.getStatus() != ApplicationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ролик к этой работе уже приложен");
        }
        Campaign campaign = application.getCampaign();
        requireAcceptingApplications(campaign);
        requireNotBlocked(creator);

        submitVideo(application, campaign, creator, request.getVideoUrl().trim());
        String comment = trimToNull(request.getComment());
        if (comment != null) {
            application.setComment(comment);
        }

        saverApplication.save(application);
        return toDto(requireApplication(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationDTO> getMyApplications() {
        User creator = currentUserService.require(Role.CREATOR);
        // Криатору показываем только вердикт: детали правил антифрода — подсказка, как их обходить
        return toDtoList(getterApplication.getByCreatorId(creator.getId())).stream()
                .map(ApplicationDTO::forCreator)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationDTO> getByCampaignId(UUID campaignId, Long ownerId) {
        Campaign campaign = getterCampaign.getById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Объявление не найдено: " + campaignId));
        if (ownerId != null && !Objects.equals(campaign.getCustomer().getId(), ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужое объявление");
        }
        // Взятые в работу без ролика заказчику решать не о чем
        return toDtoList(getterApplication.getByCampaignIdOrderByCreatedAt(campaignId).stream()
                .filter(application -> application.getStatus() != ApplicationStatus.IN_PROGRESS)
                .toList());
    }

    @Override
    @Transactional
    public ApplicationDTO updateStatus(UUID id, ApplicationStatusUpdateRequestDTO request) {
        User customer = currentUserService.require(Role.CUSTOMER);
        Application application = requireApplication(id);
        Campaign campaign = application.getCampaign();
        if (isForeign(campaign.getCustomer().getId(), customer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Отклик оставлен на чужое объявление");
        }
        if (!CUSTOMER_DECISIONS.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заказчик может только одобрить, отклонить или завершить отклик");
        }
        if (application.getStatus() == ApplicationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Криатор ещё не приложил ролик");
        }

        decide(application, request, customer);
        return toDto(requireApplication(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationDTO> getModerationQueue() {
        currentUserService.require(Role.ADMIN);
        return toDtoList(getterApplication.getAwaitingModeration());
    }

    @Override
    @Transactional
    public ApplicationDTO moderate(UUID id, ApplicationStatusUpdateRequestDTO request) {
        User manager = currentUserService.require(Role.ADMIN);
        Application application = requireApplication(id);
        if (!MODERATION_DECISIONS.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "На модерации отклик можно только одобрить или отклонить");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отклик уже не ждёт модерации: " + application.getStatus().getDescription().toLowerCase());
        }

        decide(application, request, manager);
        return toDto(requireApplication(id));
    }

    private void decide(Application application, ApplicationStatusUpdateRequestDTO request, User moderator) {
        ApplicationStatus status = request.getStatus();
        String reason = trimToNull(request.getReason());
        if (status == ApplicationStatus.REJECTED && reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину отказа: её увидит криатор");
        }
        if (application.getStatus() == ApplicationStatus.PENDING || status == ApplicationStatus.REJECTED) {
            application.setModeratedAt(Instant.now());
            application.setModeratedBy(moderator);
        }
        application.setRejectionReason(status == ApplicationStatus.REJECTED ? reason : null);
        application.setStatus(status);
        saverApplication.save(application);
        // Статус решает, идут ли по отклику деньги, поэтому бюджет объявления пересчитываем целиком
        campaignAccrualService.recalculate(application.getCampaign());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireApplication(id);
        if (isForeign(application.getCreator().getId(), creator)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        if (application.getStatus() != ApplicationStatus.PENDING
                && application.getStatus() != ApplicationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отозвать можно только отклик на рассмотрении");
        }
        // Пересчёт не нужен: по неодобренному отклику начислений нет
        applicationDeleter.deleteById(id);
    }

    @Override
    @Transactional
    public ApplicationDTO updateViews(UUID id, ViewsUpdateRequestDTO request) {
        Application application = requireApplication(id);
        Map<String, Long> countryViews = requireValidCountryViews(request);
        Instant capturedAt = Instant.now();
        application.setViews(request.getViews());
        application.setCountryViews(countryViews);
        application.setViewsSyncedAt(capturedAt);
        saverApplication.save(application);
        saverViewSnapshot.save(ApplicationViewSnapshot.builder()
                .application(application)
                .capturedAt(capturedAt)
                .views(request.getViews())
                .countryViews(countryViews)
                .source(ViewSource.MANUAL)
                .build());
        fraudCheckService.check(application);
        campaignAccrualService.recalculate(application.getCampaign());

        return toDto(requireApplication(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewSnapshotDTO> viewHistory(UUID id) {
        User user = currentUserService.require();
        Application application = requireApplication(id);
        Campaign campaign = application.getCampaign();
        boolean ownCreator = Objects.equals(application.getCreator().getId(), user.getId());
        boolean ownCustomer = campaign.getCustomer() != null
                && Objects.equals(campaign.getCustomer().getId(), user.getId());
        if (!user.getRole().isAdmin() && !ownCreator && !ownCustomer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        return getterViewSnapshot.getByApplicationId(id).stream()
                .map(ViewSnapshotDTO::from)
                .toList();
    }

    private Map<String, Long> requireValidCountryViews(ViewsUpdateRequestDTO request) {
        Map<String, Long> countryViews = request.getCountryViews();
        if (countryViews == null) {
            return null;
        }
        boolean malformed = countryViews.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null
                        || !entry.getKey().trim().matches("[A-Za-z]{2}")
                        || entry.getValue() == null
                        || entry.getValue() < 0);
        if (malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Просмотры по странам: коды стран из двух букв, значения не меньше нуля");
        }
        Map<String, Long> normalized = CountryViewsConverter.normalize(countryViews);
        long regional = normalized.values().stream().mapToLong(Long::longValue).sum();
        if (regional > request.getViews()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Просмотры по странам не могут превышать общее число просмотров");
        }
        return normalized;
    }

    /**
     * Короткие ссылки (vm.tiktok.com, youtu.be в редиректе) разворачиваем до полной:
     * иначе один и тот же ролик под двумя ссылками пройдёт как два разных.
     * Площадка недоступна — работаем с тем, что прислали.
     */
    private String resolveVideoUrl(Platform platform, String videoUrl) {
        boolean identified = switch (platform) {
            case TIKTOK -> VideoUrls.tiktokVideoId(videoUrl) != null;
            case YOUTUBE_SHORTS -> VideoUrls.youtubeVideoId(videoUrl) != null;
            default -> true;
        };
        return identified ? videoUrl : shortLinkResolver.resolve(videoUrl);
    }

    /**
     * Ролик к отклику: площадка из ссылки, аккаунт этой площадки в профиле, короткая ссылка
     * развёрнута до полной, тот же ролик нигде больше не подан. После этого отклик ждёт решения заказчика.
     */
    private void submitVideo(Application application, Campaign campaign, User creator, String rawVideoUrl) {
        Platform platform = requirePlatform(rawVideoUrl);
        requireAcceptedByCampaign(campaign, platform);
        requireConnectedAccount(creator, platform);
        String videoUrl = resolveVideoUrl(platform, rawVideoUrl);
        String videoKey = VideoUrls.videoKey(platform, videoUrl);
        requireVideoNotSubmitted(videoKey, creator);

        application.setPlatform(platform);
        application.setVideoUrl(videoUrl);
        application.setVideoKey(videoKey);
        application.setStatus(ApplicationStatus.PENDING);
    }

    private void requireAcceptingApplications(Campaign campaign) {
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Объявление не принимает отклики: " + campaign.getStatus().getDescription().toLowerCase());
        }
        requireWithinPeriod(campaign);
    }

    private void requireWithinPeriod(Campaign campaign) {
        Instant now = Instant.now();
        if (!campaign.startedBy(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Объявление начнёт принимать отклики позже — даты приёма указаны на странице объявления");
        }
        if (campaign.endedBy(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Срок действия объявления истёк");
        }
    }

    private void requireNotBlocked(User creator) {
        if (creatorTrustService.levelOf(creator.getId()).blocksApplications()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Аккаунт заблокирован за накрутку просмотров: откликаться на объявления нельзя");
        }
    }

    private void requireVideoLimitNotReached(Campaign campaign, User creator) {
        if (!campaign.limitsVideosPerCreator()) {
            return;
        }
        long submitted = getterApplication.countActiveByCampaignIdAndCreatorId(campaign.getId(), creator.getId());
        if (submitted >= campaign.getMaxVideosPerCreator()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "По этому объявлению от одного криатора принимается не больше "
                            + campaign.getMaxVideosPerCreator() + " " + videosWord(campaign.getMaxVideosPerCreator()));
        }
    }

    private static String videosWord(int count) {
        return count % 10 == 1 && count % 100 != 11 ? "ролика" : "роликов";
    }

    private Platform requirePlatform(String videoUrl) {
        Platform platform = VideoUrls.detectPlatform(videoUrl);
        if (platform == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не удалось определить площадку по ссылке: принимаются ролики YouTube, TikTok и Instagram");
        }
        return platform;
    }

    private void requireAcceptedByCampaign(Campaign campaign, Platform platform) {
        if (campaign.acceptsPlatform(platform)) {
            return;
        }
        String accepted = campaign.getPlatforms().stream()
                .sorted()
                .map(Platform::getDescription)
                .collect(Collectors.joining(", "));
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Объявление не принимает ролики с " + platform.getDescription()
                        + (accepted.isEmpty() ? "" : ": подходят только " + accepted));
    }

    private void requireConnectedAccount(User creator, Platform platform) {
        if (getterSocialAccount.getActiveByUserIdAndPlatform(creator.getId(), platform).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Привяжите аккаунт " + platform.getDescription()
                            + " в профиле: ролик должен быть выложен с подключённого аккаунта");
        }
    }

    private void requireVideoNotSubmitted(String videoKey, User creator) {
        getterApplication.getActiveByVideoKey(videoKey).ifPresent(existing -> {
            if (Objects.equals(existing.getCreator().getId(), creator.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Этот ролик уже подан по объявлению «" + existing.getCampaign().getTitle() + "»");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Этот ролик уже подан на площадке другим криатором");
        });
    }

    private Application requireApplication(UUID id) {
        return getterApplication.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отклик не найден: " + id));
    }

    /** ADMIN — служебная роль поддержки: чужие объявления и отклики ему открыты. */
    private boolean isForeign(Long ownerId, User user) {
        return !user.getRole().isAdmin() && !Objects.equals(ownerId, user.getId());
    }

    private ApplicationDTO toDto(Application application) {
        return ApplicationDTO.from(application, creatorProfile(application.getCreator().getId()));
    }

    /**
     * Профили криаторов берём одной пачкой: в DTO из профиля нужен только телеграм,
     * ради него делать запрос на каждую строку списка не за чем.
     */
    private List<ApplicationDTO> toDtoList(List<Application> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        List<Long> creatorIds = applications.stream()
                .map(application -> application.getCreator().getId())
                .distinct()
                .toList();
        Map<Long, CreatorProfile> profiles = getterCreatorProfile.getAllByUserIds(creatorIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));

        return applications.stream()
                .map(application -> ApplicationDTO.from(application, profiles.get(application.getCreator().getId())))
                .toList();
    }

    /** Профиль может быть не заполнен — в DTO тогда просто не будет телеграма. */
    private CreatorProfile creatorProfile(Long creatorId) {
        return getterCreatorProfile.getByUserId(creatorId).orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
