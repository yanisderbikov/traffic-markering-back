package ru.trafficmarkering.service.wallet.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.dto.wallet.WalletTransactionDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.repository.WalletTransactionRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.wallet.WalletAccountService;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.List;

@Service
@RequiredArgsConstructor
class WalletServiceImpl implements WalletService {

    private final CurrentUserService currentUserService;
    private final WalletAccountService walletAccountService;
    private final WalletTransactionRepository transactionRepository;

    @Override
    @Transactional
    public WalletDTO getCurrent() {
        User user = currentUserService.require();
        return WalletDTO.from(walletAccountService.getOrCreate(user));
    }

    @Override
    @Transactional
    public List<WalletTransactionDTO> getCurrentTransactions() {
        User user = currentUserService.require();
        Wallet wallet = walletAccountService.getOrCreate(user);
        return transactionRepository.findTop100ByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(WalletTransactionDTO::from)
                .toList();
    }
}
