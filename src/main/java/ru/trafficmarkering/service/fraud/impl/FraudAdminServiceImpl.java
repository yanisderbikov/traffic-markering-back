package ru.trafficmarkering.service.fraud.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.fraud.CreatorTrustDTO;
import ru.trafficmarkering.dto.fraud.FraudApplicationDTO;
import ru.trafficmarkering.dto.fraud.FraudReviewRequestDTO;
import ru.trafficmarkering.dto.fraud.TrustUpdateRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.fraud.FraudStatus;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.fraud.CreatorTrustService;
import ru.trafficmarkering.service.fraud.FraudAdminService;
import ru.trafficmarkering.service.fraud.FraudCheckService;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
class FraudAdminServiceImpl implements FraudAdminService {

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final GetterViewSnapshot getterViewSnapshot;
    private final GetterCreatorProfile getterCreatorProfile;
    private final GetterSocialAccount getterSocialAccount;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final FraudCheckService fraudCheckService;
    private final CreatorTrustService creatorTrustService;
    private final CampaignAccrualService campaignAccrualService;

    @Override
    @Transactional(readOnly = true)
    public List<FraudApplicationDTO> applications(String filter) {
        currentUserService.require(Role.ADMIN);
        List<Application> applications = getterApplication.getByFraudStatusIn(statusesFor(filter));
        List<Long> creatorIds = applications.stream().map(a -> a.getCreator().getId()).distinct().toList();
        Map<Long, CreatorProfile> profiles = getterCreatorProfile.getAllByUserIds(creatorIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));
        return applications.stream()
                .sorted((a, b) -> Integer.compare(b.fraudScoreValue(), a.fraudScoreValue()))
                .map(application -> toDto(application, profiles.get(application.getCreator().getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FraudApplicationDTO application(UUID id) {
        currentUserService.require(Role.ADMIN);
        return toDto(requireApplication(id));
    }

    @Override
    @Transactional
    public FraudApplicationDTO review(UUID id, FraudReviewRequestDTO request) {
        User admin = currentUserService.require(Role.ADMIN);
        Application application = requireApplication(id);
        boolean accruedBefore = application.isAccruable();
        String comment = request.getComment() == null || request.getComment().isBlank()
                ? null : request.getComment().trim();

        switch (request.getDecision()) {
            case VERIFIED -> {
                application.setFraudStatus(FraudStatus.VERIFIED);
                markReviewed(application, admin, comment);
            }
            case FRAUD -> {
                application.setFraudStatus(FraudStatus.FRAUD);
                markReviewed(application, admin, comment);
            }
            case AUTO -> {
                application.setFraudReviewedAt(null);
                application.setFraudReviewedBy(null);
                application.setFraudReviewComment(comment);
                saverApplication.save(application);
                fraudCheckService.check(application);
            }
        }
        saverApplication.save(application);
        if (accruedBefore != application.isAccruable()) {
            campaignAccrualService.recalculate(application.getCampaign());
        }
        creatorTrustService.refresh(application.getCreator().getId());
        log.info("Антифрод: админ {} по отклику {} решил {}", admin.getUsername(), application.getPublicId(),
                request.getDecision());
        return toDto(requireApplication(id));
    }

    @Override
    @Transactional
    public FraudApplicationDTO recheck(UUID id) {
        currentUserService.require(Role.ADMIN);
        Application application = requireApplication(id);
        if (fraudCheckService.check(application)) {
            campaignAccrualService.recalculate(application.getCampaign());
        }
        return toDto(requireApplication(id));
    }

    @Override
    @Transactional
    public int recheckAll() {
        currentUserService.require(Role.ADMIN);
        Map<UUID, Campaign> touched = new LinkedHashMap<>();
        int checked = 0;
        for (Application application : getterApplication.getAccruable()) {
            if (fraudCheckService.check(application)) {
                touched.put(application.getCampaign().getId(), application.getCampaign());
            }
            checked++;
        }
        touched.values().forEach(campaignAccrualService::recalculate);
        log.info("Антифрод: перепроверено откликов {}, пересчитано объявлений {}", checked, touched.size());
        return checked;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreatorTrustDTO> creators() {
        currentUserService.require(Role.ADMIN);
        List<User> creators = userRepository.findAllByRole(Role.CREATOR);
        Map<Long, CreatorProfile> profiles = getterCreatorProfile
                .getAllByUserIds(creators.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));
        return creators.stream()
                .map(creator -> CreatorTrustDTO.from(creator, profiles.get(creator.getId()),
                        creatorTrustService.stats(getterApplication.getByCreatorId(creator.getId()))))
                .toList();
    }

    @Override
    @Transactional
    public CreatorTrustDTO updateTrust(Long creatorId, TrustUpdateRequestDTO request) {
        User admin = currentUserService.require(Role.ADMIN);
        User creator = userRepository.findById(creatorId)
                .filter(user -> user.getRole() == Role.CREATOR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Криатор не найден: " + creatorId));
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        creatorTrustService.setManual(creatorId, request.getTrustLevel(), note, admin);
        return CreatorTrustDTO.from(creator, getterCreatorProfile.getByUserId(creatorId).orElse(null),
                creatorTrustService.stats(getterApplication.getByCreatorId(creatorId)));
    }

    private static Set<FraudStatus> statusesFor(String filter) {
        String normalized = filter == null ? "attention" : filter.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "attention" -> EnumSet.of(FraudStatus.SUSPICIOUS, FraudStatus.FRAUD);
            case "all" -> EnumSet.allOf(FraudStatus.class);
            default -> {
                try {
                    yield EnumSet.of(FraudStatus.valueOf(normalized.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Фильтр: attention, all или один из CLEAN, SUSPICIOUS, FRAUD, VERIFIED");
                }
            }
        };
    }

    private void markReviewed(Application application, User admin, String comment) {
        application.setFraudReviewedAt(Instant.now());
        application.setFraudReviewedBy(admin);
        application.setFraudReviewComment(comment);
    }

    private Application requireApplication(UUID id) {
        return getterApplication.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отклик не найден: " + id));
    }

    private FraudApplicationDTO toDto(Application application) {
        return toDto(application, getterCreatorProfile.getByUserId(application.getCreator().getId()).orElse(null));
    }

    private FraudApplicationDTO toDto(Application application, CreatorProfile profile) {
        Long followers = application.getPlatform() == null ? null
                : getterSocialAccount.getActiveByUserIdAndPlatform(application.getCreator().getId(), application.getPlatform())
                        .map(SocialAccount::getFollowers)
                        .orElse(null);
        ApplicationViewSnapshot latest = getterViewSnapshot.getByApplicationId(application.getId()).stream()
                .findFirst()
                .orElse(null);
        return FraudApplicationDTO.from(application, profile, followers, latest);
    }
}
