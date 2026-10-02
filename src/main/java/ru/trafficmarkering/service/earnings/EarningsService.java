package ru.trafficmarkering.service.earnings;

import ru.trafficmarkering.dto.earnings.CreatorWalletDTO;
import ru.trafficmarkering.dto.earnings.PayoutCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;

import java.util.List;

public interface EarningsService {

    CreatorWalletDTO myWallet();

    List<OperationRowDTO> myOperations();

    OperationDetailDTO myOperation(String publicId);

    OperationDetailDTO requestPayout(PayoutCreateRequestDTO request);

    OperationDetailDTO confirmPayout(String publicId);

    OperationDetailDTO cancelPayout(String publicId);

    int creditAccrued();
}
