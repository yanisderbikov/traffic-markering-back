package ru.trafficmarkering.service.partner.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.dto.partner.PartnerDTO;
import ru.trafficmarkering.dto.partner.PartnerInviteDTO;
import ru.trafficmarkering.dto.partner.PartnerReferralDTO;
import ru.trafficmarkering.dto.partner.PartnerRewardDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.partner.Partner;
import ru.trafficmarkering.model.partner.ReferralReward;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.GetterPartner;
import ru.trafficmarkering.repository.GetterReferralReward;
import ru.trafficmarkering.repository.SaverPartner;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.partner.PartnerService;
import ru.trafficmarkering.util.PublicIdGenerator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
class PartnerServiceImpl implements PartnerService {

    static final int RECENT_REWARDS = 20;

    private final GetterPartner getterPartner;
    private final SaverPartner saverPartner;
    private final GetterReferralReward getterReferralReward;
    private final GetterCustomerProfile getterCustomerProfile;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final CommissionProperties commissionProperties;

    @Override
    @Transactional(readOnly = true)
    public PartnerDTO my() {
        User customer = currentUserService.require(Role.CUSTOMER);
        return getterPartner.getByUserId(customer.getId())
                .map(this::toDTO)
                .orElseGet(this::inactive);
    }

    @Override
    @Transactional
    public PartnerDTO activate() {
        User customer = currentUserService.require(Role.CUSTOMER);
        Partner partner = getterPartner.getByUserId(customer.getId()).orElseGet(() -> {
            Partner created = saverPartner.save(Partner.builder()
                    .user(customer)
                    .code(PublicIdGenerator.generateUnique(getterPartner::existsByCode))
                    .build());
            log.info("Рекламодатель {} подключил партнёрскую программу, код {}", customer.getUsername(), created.getCode());
            return created;
        });
        return toDTO(partner);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerInviteDTO invite(String code) {
        Partner partner = findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));
        User owner = partner.getUser();
        String company = getterCustomerProfile.getByUserId(owner.getId())
                .map(CustomerProfile::getCompany)
                .filter(name -> !name.isBlank())
                .map(String::trim)
                .orElse(null);
        return new PartnerInviteDTO(partner.getCode(), owner.getName(), company);
    }

    @Override
    public Optional<Partner> findByCode(String code) {
        String normalized = Partner.normalizeCode(code);
        if (normalized.isEmpty() || normalized.length() > Partner.CODE_MAX_LENGTH) {
            return Optional.empty();
        }
        return getterPartner.getByCode(normalized);
    }

    @Override
    public boolean isPartner(User user) {
        return user.getRole().implies(Role.CUSTOMER) && getterPartner.getByUserId(user.getId()).isPresent();
    }

    private PartnerDTO inactive() {
        return new PartnerDTO(false, null, null, commissionProperties.getPercent(),
                commissionProperties.getPartnerSharePercent(), 0, 0, 0L, List.of(), List.of());
    }

    private PartnerDTO toDTO(Partner partner) {
        List<User> referrals = userRepository
                .findAllByReferredByIdAndVerifiedAtIsNotNullOrderByCreatedAtDesc(partner.getId());
        List<ReferralReward> rewards = getterReferralReward.getByPartnerId(partner.getId());
        Map<Long, List<ReferralReward>> rewardsByReferral = rewards.stream()
                .collect(Collectors.groupingBy(reward -> reward.getReferral().getId()));
        return new PartnerDTO(
                true,
                partner.getCode(),
                partner.getCreatedAt() != null ? partner.getCreatedAt().toString() : null,
                commissionProperties.getPercent(),
                commissionProperties.getPartnerSharePercent(),
                referrals.size(),
                rewardsByReferral.size(),
                rewards.stream().mapToLong(ReferralReward::reward).sum(),
                referrals.stream()
                        .map(referral -> referralDTO(referral, rewardsByReferral.getOrDefault(referral.getId(), List.of())))
                        .toList(),
                rewards.stream().limit(RECENT_REWARDS).map(PartnerRewardDTO::from).toList());
    }

    private PartnerReferralDTO referralDTO(User referral, List<ReferralReward> rewards) {
        return new PartnerReferralDTO(
                referral.getName(),
                referral.getCreatedAt() != null ? referral.getCreatedAt().toString() : null,
                rewards.stream().mapToLong(ReferralReward::reward).sum(),
                rewards.size());
    }
}
