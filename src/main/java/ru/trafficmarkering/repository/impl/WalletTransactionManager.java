package ru.trafficmarkering.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;

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
    public Optional<WalletTransaction> getByPublicIdWithDetails(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        try {
            return walletTransactionRepo.findByPublicIdWithDetails(publicId.trim());
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<WalletTransaction> getByPublicIdForUpdate(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        try {
            Long id = walletTransactionRepo.findIdByPublicId(publicId.trim()).orElse(null);
            if (id == null) {
                return Optional.empty();
            }
            WalletTransaction transaction = entityManager.find(WalletTransaction.class, id);
            if (transaction == null) {
                return Optional.empty();
            }
            entityManager.refresh(transaction, LockModeType.PESSIMISTIC_WRITE);
            return Optional.of(transaction);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean existsByPublicId(String publicId) {
        try {
            return walletTransactionRepo.existsByPublicId(publicId);
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
