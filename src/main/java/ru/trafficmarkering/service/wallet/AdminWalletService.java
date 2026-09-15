package ru.trafficmarkering.service.wallet;

import ru.trafficmarkering.dto.wallet.AdminWalletAdjustmentRequestDTO;
import ru.trafficmarkering.dto.wallet.AdminWalletDTO;
import ru.trafficmarkering.dto.wallet.WalletTransactionDTO;

import java.util.List;

public interface AdminWalletService {
    List<AdminWalletDTO> getWallets();

    List<WalletTransactionDTO> getTransactions(Long userId);

    AdminWalletDTO adjust(Long userId, AdminWalletAdjustmentRequestDTO request);
}
