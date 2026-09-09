package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ViewSnapshotDTO;
import ru.trafficmarkering.dto.application.ViewsUpdateRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.ApplicationDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.application.ApplicationService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.util.PublicIdGenerator;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final ApplicationDeleter applicationDeleter;
    private final GetterCampaign getterCampaign;
    private final GetterCreatorProfile getterCreatorProfile;
    private final CurrentUserService currentUserService;
    private final CampaignAccrualService campaignAccrualService;
    private final GetterViewSnapshot getterViewSnapshot;
    private final SaverViewSnapshot saverViewSnapshot;

    @Override
    @Transactional
    public ApplicationDTO apply(ApplicationCreateRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        Campaign campaign = getterCampaign.getById(request.getCampaignId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Объявление не найдено: " + request.getCampaignId()));

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Объявление не принимает отклики: " + campaign.getStatus().getDescription().toLowerCase());
        }
        // ADMIN проходит проверку роли выше, поэтому теоретически может оказаться и заказчиком
        if (Objects.equals(campaign.getCustomer().getId(), creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Нельзя откликнуться на собственное объявление");
        }
        if (getterApplication.existsByCampaignIdAndCreatorId(campaign.getId(), creator.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Вы уже откликались на это объявление");
        }

        Application application = Application.builder()
                .publicId(PublicIdGenerator.generateUnique(getterApplication::existsByPublicId))
                .campaign(campaign)
                .creator(creator)
                .platform(request.getPlatform())
                .videoUrl(request.getVideoUrl().trim())
                .comment(trimToNull(request.getComment()))
                .status(ApplicationStatus.PENDING)
                .build();

        Application saved = saverApplication.save(application);
        return ApplicationDTO.from(saved, campaign, creator, creatorProfile(creator.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationDTO> getMyApplications() {
        User creator = currentUserService.require(Role.CREATOR);
        return toDtoList(getterApplication.getByCreatorId(creator.getId()));
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
        return toDtoList(getterApplication.getByCampaignIdOrderByCreatedAt(campaignId));
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

        application.setStatus(request.getStatus());
        saverApplication.save(application);
        // Статус решает, идут ли по отклику деньги, поэтому бюджет объявления пересчитываем целиком
        campaignAccrualService.recalculate(campaign);

        return toDto(requireApplication(id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        User creator = currentUserService.require(Role.CREATOR);
        Application application = requireApplication(id);
        if (isForeign(application.getCreator().getId(), creator)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
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
        Instant capturedAt = Instant.now();
        application.setViews(request.getViews());
        application.setViewsSyncedAt(capturedAt);
        saverApplication.save(application);
        saverViewSnapshot.save(ApplicationViewSnapshot.builder()
                .application(application)
                .capturedAt(capturedAt)
                .views(request.getViews())
                .source(ViewSource.MANUAL)
                .build());
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
        if (user.getRole() != Role.ADMIN && !ownCreator && !ownCustomer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой отклик");
        }
        return getterViewSnapshot.getByApplicationId(id).stream()
                .map(ViewSnapshotDTO::from)
                .toList();
    }

    private Application requireApplication(UUID id) {
        return getterApplication.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отклик не найден: " + id));
    }

    /** ADMIN — служебная роль поддержки: чужие объявления и отклики ему открыты. */
    private boolean isForeign(Long ownerId, User user) {
        return user.getRole() != Role.ADMIN && !Objects.equals(ownerId, user.getId());
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
