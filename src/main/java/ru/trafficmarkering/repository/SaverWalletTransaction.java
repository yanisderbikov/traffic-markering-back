package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.wallet.WalletTransaction;

public interface SaverWalletTransaction {
    WalletTransaction save(WalletTransaction transaction);
}
