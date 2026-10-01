package ru.trafficmarkering.service.transfer;

import ru.trafficmarkering.dto.transfer.TransferRejectRequestDTO;
import ru.trafficmarkering.dto.transfer.TransferSentRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.TopUpCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.TopUpPaidRequestDTO;
import ru.trafficmarkering.dto.wallet.WalletOperationRequestDTO;

import java.util.List;

public interface TransferService {

    OperationDetailDTO requestTopUp(TopUpCreateRequestDTO request);

    OperationDetailDTO markTopUpPaid(String publicId, TopUpPaidRequestDTO request);

    OperationDetailDTO cancelTopUp(String publicId);

    int expireOverdue();

    int fixMissingUsdtRates();

    List<OperationRowDTO> topUps();

    OperationDetailDTO confirmTopUp(String publicId);

    OperationDetailDTO withdraw(Long userId, WalletOperationRequestDTO request);

    List<OperationRowDTO> payouts();

    OperationDetailDTO markPayoutSent(String publicId, TransferSentRequestDTO request);

    OperationDetailDTO reject(String publicId, TransferRejectRequestDTO request);
}
