package ru.trafficmarkering.service.partner;

import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;

public interface ReferralRewardService {

    void reward(WalletTransaction source, Transfer transfer);
}
