package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.partner.ReferralReward;

import java.util.List;

public interface GetterReferralReward {

    boolean existsBySourceTransactionId(Long transactionId);

    List<ReferralReward> getByPartnerId(Long partnerId);
}
