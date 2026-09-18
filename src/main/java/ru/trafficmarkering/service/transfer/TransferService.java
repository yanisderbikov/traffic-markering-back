package ru.trafficmarkering.service.transfer;

import ru.trafficmarkering.dto.transfer.TransferRejectRequestDTO;
import ru.trafficmarkering.dto.transfer.TransferSentRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.WalletOperationRequestDTO;

import java.util.List;

public interface TransferService {

    OperationDetailDTO topUp(Long userId, WalletOperationRequestDTO request);

    OperationDetailDTO withdraw(Long userId, WalletOperationRequestDTO request);

    List<OperationRowDTO> payouts();

    OperationDetailDTO markPayoutSent(Long id, TransferSentRequestDTO request);

    OperationDetailDTO reject(Long id, TransferRejectRequestDTO request);
}
