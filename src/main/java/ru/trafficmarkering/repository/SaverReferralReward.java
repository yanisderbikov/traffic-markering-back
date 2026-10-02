package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.partner.ReferralReward;

public interface SaverReferralReward {
    ReferralReward save(ReferralReward referralReward);
}
