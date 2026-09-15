package ru.trafficmarkering.service.wallet;

import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Wallet;

/** Shared wallet provisioning/locking logic used by user and admin use-cases. */
public interface WalletAccountService {
    Wallet getOrCreate(User user);

    /** Returns the wallet with a database write lock held until the surrounding transaction ends. */
    Wallet getOrCreateForUpdate(User user);
}
