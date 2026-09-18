package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.SaverTransfer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class TransferManager implements GetterTransfer, SaverTransfer {

    private final TransferRepo transferRepo;

    @Override
    public Optional<Transfer> getByTransactionId(Long transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        try {
            return transferRepo.findByTransactionId(transactionId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Transfer> getByTransactionIds(Collection<Long> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return List.of();
        }
        try {
            return transferRepo.findAllByTransactionIdIn(transactionIds);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Transfer save(Transfer transfer) {
        try {
            return transferRepo.saveAndFlush(transfer);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
