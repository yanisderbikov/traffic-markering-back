package ru.trafficmarkering.service.wallet.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.wallet.AdminWalletAdjustmentRequestDTO;
import ru.trafficmarkering.dto.wallet.AdminWalletDTO;
import ru.trafficmarkering.dto.wallet.WalletTransactionDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.repository.WalletRepository;
import ru.trafficmarkering.repository.WalletTransactionRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.wallet.AdminWalletService;
import ru.trafficmarkering.service.wallet.WalletAccountService;

import java.util.List;

@Service
@RequiredArgsConstructor
class AdminWalletServiceImpl implements AdminWalletService {

    private static final List<Role> WALLET_ROLES = List.of(Role.CUSTOMER, Role.CREATOR);

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletAccountService walletAccountService;

    @Override
    @Transactional
    public List<AdminWalletDTO> getWallets() {
        currentUserService.require(Role.ADMIN);
        return userRepository.findAllByRoleInOrderByNameAsc(WALLET_ROLES).stream()
                .map(walletAccountService::getOrCreate)
                .map(AdminWalletDTO::from)
                .toList();
    }

    @Override
    @Transactional
    public List<WalletTransactionDTO> getTransactions(Long userId) {
        currentUserService.require(Role.ADMIN);
        User target = requireWalletUser(userId);
        Wallet wallet = walletAccountService.getOrCreate(target);
        return transactionRepository.findTop100ByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(WalletTransactionDTO::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminWalletDTO adjust(Long userId, AdminWalletAdjustmentRequestDTO request) {
        User admin = currentUserService.require(Role.ADMIN);
        User target = requireWalletUser(userId);
        Wallet wallet = walletAccountService.getOrCreateForUpdate(target);

        long amount = request.amountKopecks() == null ? 0L : request.amountKopecks();
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (amount == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма изменения не может быть нулевой");
        }
        if (reason.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину изменения баланса");
        }

        try {
            wallet.adjust(amount);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма изменения слишком велика");
        }

        Wallet saved = walletRepository.saveAndFlush(wallet);
        transactionRepository.save(WalletTransaction.builder()
                .wallet(saved)
                .actor(admin)
                .type(WalletTransactionType.ADMIN_ADJUSTMENT)
                .amountKopecks(amount)
                .balanceAfterKopecks(saved.getBalanceKopecks())
                .reason(reason)
                .build());

        return AdminWalletDTO.from(saved);
    }

    private User requireWalletUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        if (!WALLET_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Баланс можно менять только заказчику или криатору");
        }
        return user;
    }
}
