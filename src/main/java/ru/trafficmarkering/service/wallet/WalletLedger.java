package ru.trafficmarkering.service.wallet;

import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

public interface WalletLedger {

    Wallet walletOf(User owner);

    Wallet lockWallet(User owner);

    WalletTransaction post(Wallet wallet,
                           WalletTransactionType type,
                           long signedAmountKopecks,
                           WalletTransactionStatus status,
                           Campaign campaign,
                           User actor,
                           String comment);

    void restore(WalletTransaction transaction, WalletTransactionStatus finalStatus);
}
