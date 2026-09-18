package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.wallet.Wallet;

public interface SaverWallet {
    Wallet save(Wallet wallet);
}
