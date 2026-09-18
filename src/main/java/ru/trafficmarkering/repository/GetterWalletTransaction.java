package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

import java.util.List;
import java.util.Optional;

public interface GetterWalletTransaction {

    List<WalletTransaction> getByWalletId(Long walletId);

    Optional<WalletTransaction> getByIdWithDetails(Long id);

    List<WalletTransaction> getByType(WalletTransactionType type);

    List<WalletTransaction> getAllWithDetails();
}
