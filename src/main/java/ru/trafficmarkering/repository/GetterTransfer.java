package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GetterTransfer {

    Optional<Transfer> getByTransactionId(Long transactionId);

    List<Transfer> getByTransactionIds(Collection<Long> transactionIds);

    List<Transfer> getOverdue(WalletTransactionType type, WalletTransactionStatus status, Instant now);

    List<Transfer> getWithoutUsdtRate(Collection<WalletTransactionStatus> statuses);
}
