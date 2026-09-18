package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverWalletTransaction;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class WalletTransactionManager implements GetterWalletTransaction, SaverWalletTransaction {

    private final WalletTransactionRepo walletTransactionRepo;

    @Override
    public List<WalletTransaction> getByWalletId(Long walletId) {
        if (walletId == null) {
            return List.of();
        }
        try {
            return walletTransactionRepo.findByWalletId(walletId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<WalletTransaction> getByIdWithDetails(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return walletTransactionRepo.findByIdWithDetails(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<WalletTransaction> getByType(WalletTransactionType type) {
        try {
            return walletTransactionRepo.findByType(type);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<WalletTransaction> getAllWithDetails() {
        try {
            return walletTransactionRepo.findAllWithDetails();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        try {
            return walletTransactionRepo.saveAndFlush(transaction);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
