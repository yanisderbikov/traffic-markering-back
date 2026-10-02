package ru.trafficmarkering.service.wallet.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.wallet.OperationReader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class OperationReaderImpl implements OperationReader {

    private final GetterTransfer getterTransfer;
    private final FileStorage fileStorage;

    @Override
    public List<OperationRowDTO> rows(List<WalletTransaction> transactions) {
        Set<Long> externalIds = transactions.stream()
                .filter(transaction -> transaction.getType().isExternal())
                .map(WalletTransaction::getId)
                .collect(Collectors.toSet());
        Map<Long, Transfer> transfers = externalIds.isEmpty() ? Map.of()
                : getterTransfer.getByTransactionIds(externalIds).stream()
                .collect(Collectors.toMap(transfer -> transfer.getTransaction().getId(), Function.identity()));
        return transactions.stream()
                .map(transaction -> OperationRowDTO.from(transaction, transfers.get(transaction.getId())))
                .toList();
    }

    @Override
    public OperationDetailDTO detail(WalletTransaction transaction) {
        Transfer transfer = transaction.getType().isExternal()
                ? getterTransfer.getByTransactionId(transaction.getId()).orElse(null)
                : null;
        return detail(transaction, transfer);
    }

    @Override
    public OperationDetailDTO detail(WalletTransaction transaction, Transfer transfer) {
        return OperationDetailDTO.from(transaction, transfer, fileStorage::presignedUrl);
    }
}
