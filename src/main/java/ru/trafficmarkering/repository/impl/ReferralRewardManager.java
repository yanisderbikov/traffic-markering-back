package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.partner.ReferralReward;
import ru.trafficmarkering.repository.GetterReferralReward;
import ru.trafficmarkering.repository.SaverReferralReward;

import java.util.List;

@Component
@AllArgsConstructor
@Log4j2
class ReferralRewardManager implements GetterReferralReward, SaverReferralReward {

    private final ReferralRewardRepo referralRewardRepo;

    @Override
    public boolean existsBySourceTransactionId(Long transactionId) {
        if (transactionId == null) {
            return false;
        }
        try {
            return referralRewardRepo.existsBySourceTransactionId(transactionId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<ReferralReward> getByPartnerId(Long partnerId) {
        if (partnerId == null) {
            return List.of();
        }
        try {
            return referralRewardRepo.findAllByPartnerIdWithDetails(partnerId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public ReferralReward save(ReferralReward referralReward) {
        try {
            return referralRewardRepo.saveAndFlush(referralReward);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
