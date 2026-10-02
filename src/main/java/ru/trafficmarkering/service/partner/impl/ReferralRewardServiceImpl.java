package ru.trafficmarkering.service.partner.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.partner.Partner;
import ru.trafficmarkering.model.partner.ReferralReward;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterReferralReward;
import ru.trafficmarkering.repository.SaverReferralReward;
import ru.trafficmarkering.service.partner.ReferralRewardService;
import ru.trafficmarkering.service.wallet.WalletLedger;

@Service
@RequiredArgsConstructor
@Log4j2
class ReferralRewardServiceImpl implements ReferralRewardService {

    private final WalletLedger ledger;
    private final GetterReferralReward getterReferralReward;
    private final SaverReferralReward saverReferralReward;
    private final CommissionProperties commissionProperties;

    @Override
    @Transactional
    public void reward(WalletTransaction source, Transfer transfer) {
        User referral = source.getWallet().getUser();
        Partner partner = referral.getReferredBy();
        if (partner == null || getterReferralReward.existsBySourceTransactionId(source.getId())) {
            return;
        }
        long commission = transfer.commission();
        long reward = commissionProperties.partnerShareOf(commission);
        if (reward <= 0) {
            return;
        }
        Wallet wallet = ledger.lockWallet(partner.getUser());
        WalletTransaction rewardTransaction = ledger.post(wallet, WalletTransactionType.REFERRAL_REWARD, reward,
                WalletTransactionStatus.DONE, null, null, "Партнёрская доля · " + referral.getName());
        saverReferralReward.save(ReferralReward.builder()
                .partner(partner)
                .referral(referral)
                .sourceTransaction(source)
                .rewardTransaction(rewardTransaction)
                .commissionKopecks(commission)
                .rewardKopecks(reward)
                .build());
        log.info("Партнёру {} начислено {} коп. с комиссии {} коп. по операции {}",
                partner.getCode(), reward, commission, source.getPublicId());
    }
}
