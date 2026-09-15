package ru.trafficmarkering.service.wallet;

import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.dto.wallet.WalletTransactionDTO;

import java.util.List;

public interface WalletService {
    WalletDTO getCurrent();

    List<WalletTransactionDTO> getCurrentTransactions();
}
