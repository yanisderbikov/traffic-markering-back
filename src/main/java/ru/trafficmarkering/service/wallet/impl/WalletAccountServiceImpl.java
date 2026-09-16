package ru.trafficmarkering.service.wallet.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.repository.WalletRepository;
import ru.trafficmarkering.service.wallet.WalletAccountService;

@Service
@RequiredArgsConstructor
class WalletAccountServiceImpl implements WalletAccountService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Wallet getOrCreate(User user) {
        ensureWalletRole(user);
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> createWithUserLock(user.getId()));
    }

    @Override
    @Transactional
    public Wallet getOrCreateForUpdate(User user) {
        ensureWalletRole(user);
        return walletRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> createWithUserLock(user.getId()));
    }

    /**
     * A user row lock makes the first wallet creation idempotent under concurrent requests.
     * After the wallet exists, monetary writes are serialized by the wallet row lock itself.
     */
    private Wallet createWithUserLock(Long userId) {
        User lockedUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        ensureWalletRole(lockedUser);

        return walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.saveAndFlush(Wallet.builder()
                        .user(lockedUser)
                        .balanceKopecks(0L)
                        .build()));
    }

    private void ensureWalletRole(User user) {
        if (user == null || (user.getRole() != Role.CUSTOMER && user.getRole() != Role.CREATOR)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Кошелёк доступен только заказчику или криатору");
        }
    }
}
