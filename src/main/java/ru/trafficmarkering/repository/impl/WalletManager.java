package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.SaverWallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class WalletManager implements GetterWallet, SaverWallet {

    private final WalletRepo walletRepo;

    @Override
    public Optional<Wallet> getByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return walletRepo.findByUserId(userId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Wallet> getByUserIdForUpdate(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return walletRepo.findByUserIdForUpdate(userId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Wallet> getAllWithUserByRoles(Collection<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        try {
            return walletRepo.findAllWithUserByRoleIn(roles);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Wallet save(Wallet wallet) {
        try {
            return walletRepo.saveAndFlush(wallet);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
