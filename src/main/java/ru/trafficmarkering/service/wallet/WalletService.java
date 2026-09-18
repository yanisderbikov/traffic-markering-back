package ru.trafficmarkering.service.wallet;

import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

import java.util.List;

public interface WalletService {

    WalletDTO myWallet();

    List<OperationRowDTO> myOperations();

    OperationDetailDTO myOperation(Long id);

    OperationDetailDTO confirm(Long id);

    List<WalletDTO> customers();

    WalletDTO customer(Long userId);

    User requireCustomer(Long userId);

    List<OperationRowDTO> operations(Long userId, WalletTransactionType type, WalletTransactionStatus status);

    OperationDetailDTO operation(Long id);

    void reallocate(Campaign campaign, long previousBudgetKopecks, long nextBudgetKopecks);

    void releaseBeforeDelete(Campaign campaign);
}
