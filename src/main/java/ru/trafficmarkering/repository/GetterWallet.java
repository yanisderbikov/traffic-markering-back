package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.wallet.Wallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GetterWallet {

    Optional<Wallet> getByUserId(Long userId);

    Optional<Wallet> getByUserIdForUpdate(Long userId);

    List<Wallet> getAllWithUserByRoles(Collection<Role> roles);
}
