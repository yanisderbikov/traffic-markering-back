package ru.trafficmarkering.service.wallet;

import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;

import java.util.List;

public interface OperationReader {

    List<OperationRowDTO> rows(List<WalletTransaction> transactions);

    OperationDetailDTO detail(WalletTransaction transaction);

    OperationDetailDTO detail(WalletTransaction transaction, Transfer transfer);
}
