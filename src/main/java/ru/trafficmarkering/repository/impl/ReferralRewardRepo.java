package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.partner.ReferralReward;

import java.util.List;

@Repository
interface ReferralRewardRepo extends JpaRepository<ReferralReward, Long> {

    boolean existsBySourceTransactionId(Long transactionId);

    @Query("select r from ReferralReward r join fetch r.referral join fetch r.sourceTransaction "
            + "join fetch r.rewardTransaction where r.partner.id = :partnerId order by r.createdAt desc")
    List<ReferralReward> findAllByPartnerIdWithDetails(@Param("partnerId") Long partnerId);
}
