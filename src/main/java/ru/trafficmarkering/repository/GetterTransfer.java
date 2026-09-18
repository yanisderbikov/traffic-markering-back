package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.wallet.Transfer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GetterTransfer {

    Optional<Transfer> getByTransactionId(Long transactionId);

    List<Transfer> getByTransactionIds(Collection<Long> transactionIds);
}
