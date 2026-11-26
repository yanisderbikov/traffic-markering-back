package ru.trafficmarkering.service.transfer.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.controller.FileController;
import ru.trafficmarkering.dto.transfer.TransferRejectRequestDTO;
import ru.trafficmarkering.dto.transfer.TransferSentRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.TopUpCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.TopUpPaidRequestDTO;
import ru.trafficmarkering.dto.wallet.WalletOperationRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverTransfer;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.partner.ReferralRewardService;
import ru.trafficmarkering.service.rate.UsdtRateService;
import ru.trafficmarkering.service.transfer.TransferService;
import ru.trafficmarkering.service.wallet.OperationReader;
import ru.trafficmarkering.service.wallet.WalletLedger;
import ru.trafficmarkering.service.wallet.WalletService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
class TransferServiceImpl implements TransferService {

    static final Duration TOP_UP_TTL = Duration.ofHours(1);
    static final Duration WITHDRAWAL_TTL = Duration.ofHours(1);

    /** Заявки, по которым ещё могут ходить деньги: им нужен курс, даже если они созданы до его фиксации */
    private static final Set<WalletTransactionStatus> UNSETTLED = EnumSet.of(
            WalletTransactionStatus.PENDING, WalletTransactionStatus.SENT, WalletTransactionStatus.EXPIRED);

    private static final Comparator<WalletTransaction> OPEN_FIRST = Comparator
            .comparingInt((WalletTransaction transaction) -> transaction.getStatus().isOpen() ? 0 : 1)
            .thenComparing(WalletTransaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final WalletLedger ledger;
    private final OperationReader operationReader;
    private final GetterWalletTransaction getterWalletTransaction;
    private final GetterTransfer getterTransfer;
    private final SaverTransfer saverTransfer;
    private final WalletService walletService;
    private final CurrentUserService currentUserService;
    private final UsdtRateService usdtRateService;
    private final CommissionProperties commissionProperties;
    private final ReferralRewardService referralRewardService;

    @Override
    @Transactional
    public OperationDetailDTO requestTopUp(TopUpCreateRequestDTO request) {
        User customer = currentUserService.require(Role.CUSTOMER);
        String address = walletService.topUpTronAddress();
        if (address == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Адрес для пополнения ещё не настроен — напишите менеджеру финансов");
        }
        BigDecimal usdtRate = usdtRateService.current().askPrice();
        Wallet wallet = ledger.walletOf(customer);
        WalletTransaction transaction = ledger.defer(wallet, WalletTransactionType.TOP_UP, request.getAmountKopecks(),
                WalletTransactionStatus.PENDING, customer);
        Transfer transfer = saverTransfer.save(Transfer.builder()
                .transaction(transaction)
                .tronAddress(address)
                .usdtRate(usdtRate)
                .commissionKopecks(commissionProperties.commissionOf(request.getAmountKopecks()))
                .expiresAt(Instant.now().plus(TOP_UP_TTL))
                .build());
        return detail(transaction, transfer);
    }

    @Override
    @Transactional
    public OperationDetailDTO markTopUpPaid(String publicId, TopUpPaidRequestDTO request) {
        User customer = currentUserService.require(Role.CUSTOMER);
        WalletTransaction transaction = requireOwnTopUp(publicId, customer);
        if (transaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отметить оплату можно только у новой заявки, сейчас она "
                            + transaction.getStatus().getDescription().toLowerCase());
        }
        Transfer transfer = requireTransfer(transaction);
        if (transfer.isExpired(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Время на оплату заявки вышло — создайте новую заявку на пополнение");
        }
        transfer.attach(trimToNull(request.getTxId()), requireProofKeys(request.getProofKeys()));
        saverTransfer.save(transfer);
        transaction.setStatus(WalletTransactionStatus.SENT);
        return detail(transaction, transfer);
    }

    @Override
    @Transactional
    public OperationDetailDTO cancelTopUp(String publicId) {
        User customer = currentUserService.require(Role.CUSTOMER);
        WalletTransaction transaction = requireOwnTopUp(publicId, customer);
        if (transaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отменить можно только заявку, которую вы ещё не оплатили");
        }
        Transfer transfer = requireTransfer(transaction);
        transfer.cancel();
        saverTransfer.save(transfer);
        transaction.setStatus(WalletTransactionStatus.CANCELLED);
        return detail(transaction, transfer);
    }

    @Override
    @Transactional
    public int expireOverdue() {
        Instant now = Instant.now();
        return expireOverdue(WalletTransactionType.TOP_UP, WalletTransactionStatus.PENDING, now)
                + expireOverdue(WalletTransactionType.WITHDRAWAL, WalletTransactionStatus.SENT, now);
    }

    @Override
    @Transactional
    public int fixMissingUsdtRates() {
        List<Transfer> missing = getterTransfer.getWithoutUsdtRate(UNSETTLED);
        if (missing.isEmpty()) {
            return 0;
        }
        BigDecimal usdtRate = usdtRateService.current().askPrice();
        for (Transfer transfer : missing) {
            transfer.setUsdtRate(usdtRate);
            saverTransfer.save(transfer);
        }
        return missing.size();
    }

    private int expireOverdue(WalletTransactionType type, WalletTransactionStatus waiting, Instant now) {
        int expired = 0;
        for (Transfer overdue : getterTransfer.getOverdue(type, waiting, now)) {
            WalletTransaction transaction = getterWalletTransaction
                    .getByPublicIdForUpdate(overdue.getTransaction().getPublicId())
                    .orElse(null);
            if (transaction == null || transaction.getStatus() != waiting) {
                continue;
            }
            overdue.expire();
            saverTransfer.save(overdue);
            transaction.setStatus(WalletTransactionStatus.EXPIRED);
            expired++;
        }
        return expired;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationRowDTO> topUps() {
        currentUserService.require(Role.FINANCE_MANAGER);
        return operationReader.rows(getterWalletTransaction.getByType(WalletTransactionType.TOP_UP).stream()
                .sorted(OPEN_FIRST)
                .toList());
    }

    @Override
    @Transactional
    public OperationDetailDTO confirmTopUp(String publicId) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        WalletTransaction transaction = requireExternal(publicId);
        if (transaction.getType() != WalletTransactionType.TOP_UP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Это не заявка на пополнение");
        }
        if (!transaction.getStatus().isOpen() && transaction.getStatus() != WalletTransactionStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Заявка уже закрыта: " + transaction.getStatus().getDescription().toLowerCase());
        }
        Transfer transfer = requireTransfer(transaction);
        ledger.settle(transaction, WalletTransactionStatus.CONFIRMED);
        transfer.confirm(actor);
        saverTransfer.save(transfer);
        referralRewardService.reward(transaction, transfer);
        return detail(transaction, transfer);
    }

    @Override
    @Transactional
    public OperationDetailDTO withdraw(Long userId, WalletOperationRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        User customer = walletService.requireCustomer(userId);
        String address = requireTronAddress(request.getTronAddress());
        BigDecimal usdtRate = usdtRateService.current().askPrice();
        Wallet wallet = ledger.lockWallet(customer);
        WalletTransaction transaction = ledger.post(wallet, WalletTransactionType.WITHDRAWAL, -request.getAmountKopecks(),
                WalletTransactionStatus.SENT, null, actor, trimToNull(request.getComment()));
        return detail(transaction, sentTransfer(transaction, address, usdtRate, actor, request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationRowDTO> payouts() {
        currentUserService.require(Role.FINANCE_MANAGER);
        return operationReader.rows(getterWalletTransaction.getByType(WalletTransactionType.PAYOUT).stream()
                .sorted(OPEN_FIRST)
                .toList());
    }

    @Override
    @Transactional
    public OperationDetailDTO markPayoutSent(String publicId, TransferSentRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        WalletTransaction transaction = requireExternal(publicId);
        if (transaction.getType() != WalletTransactionType.PAYOUT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Это не заявка на выплату");
        }
        if (transaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отметить отправленной можно только заявку в ожидании, сейчас она "
                            + transaction.getStatus().getDescription().toLowerCase());
        }
        Transfer transfer = requireTransfer(transaction);
        transfer.send(actor, request.getTxId().trim(), requireProofKeys(request.getProofKeys()),
                trimToNull(request.getComment()));
        saverTransfer.save(transfer);
        transaction.setStatus(WalletTransactionStatus.SENT);
        return detail(transaction, transfer);
    }

    @Override
    @Transactional
    public OperationDetailDTO reject(String publicId, TransferRejectRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        WalletTransaction transaction = requireExternal(publicId);
        boolean expiredWithdrawal = transaction.getType() == WalletTransactionType.WITHDRAWAL
                && transaction.getStatus() == WalletTransactionStatus.EXPIRED;
        if (!transaction.getStatus().isOpen() && !expiredWithdrawal) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Операция уже закрыта: " + transaction.getStatus().getDescription().toLowerCase());
        }
        Transfer transfer = requireTransfer(transaction);
        if (transaction.getType().settlesOnConfirm()) {
            transaction.setStatus(WalletTransactionStatus.REJECTED);
        } else {
            ledger.restore(transaction, WalletTransactionStatus.REJECTED);
        }
        transfer.reject(actor, request.getReason().trim());
        saverTransfer.save(transfer);
        return detail(transaction, transfer);
    }

    private Transfer sentTransfer(WalletTransaction transaction, String tronAddress, BigDecimal usdtRate, User actor,
                                  WalletOperationRequestDTO request) {
        Transfer transfer = Transfer.builder()
                .transaction(transaction)
                .tronAddress(tronAddress)
                .usdtRate(usdtRate)
                .commissionKopecks(commissionProperties.commissionOf(request.getAmountKopecks()))
                .expiresAt(Instant.now().plus(WITHDRAWAL_TTL))
                .build();
        transfer.send(actor, request.getTxId().trim(), requireProofKeys(request.getProofKeys()), null);
        return saverTransfer.save(transfer);
    }

    private WalletTransaction requireExternal(String publicId) {
        return requireExternal(publicId, transaction -> true);
    }

    private WalletTransaction requireExternal(String publicId, Predicate<WalletTransaction> accessible) {
        WalletTransaction transaction = getterWalletTransaction.getByPublicIdForUpdate(publicId)
                .filter(accessible)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Операция не найдена: " + publicId));
        if (!transaction.getType().isExternal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Это внутренняя операция платформы, у неё нет перевода");
        }
        return transaction;
    }

    private WalletTransaction requireOwnTopUp(String publicId, User customer) {
        WalletTransaction transaction = requireExternal(publicId, found -> found.belongsTo(customer));
        if (transaction.getType() != WalletTransactionType.TOP_UP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Это не заявка на пополнение");
        }
        return transaction;
    }

    private Transfer requireTransfer(WalletTransaction transaction) {
        return getterTransfer.getByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Перевод не найден для операции " + transaction.getPublicId()));
    }

    private String requireTronAddress(String value) {
        String address = value == null ? "" : value.trim();
        if (!Transfer.isTronAddress(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Адрес не похож на кошелёк TRON (TRC-20): он начинается с T и состоит из 34 символов");
        }
        return address;
    }

    private List<String> requireProofKeys(List<String> keys) {
        List<String> proofKeys = keys == null ? List.of()
                : keys.stream().map(String::trim).filter(key -> !key.isEmpty()).toList();
        if (proofKeys.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Приложите хотя бы один скриншот или файл перевода");
        }
        for (String key : proofKeys) {
            if (!key.startsWith(FileController.TRANSFER_PROOF_PREFIX + "/") || key.contains("..")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ключ файла не из загрузки подтверждений переводов: " + key);
            }
        }
        return proofKeys;
    }

    private OperationDetailDTO detail(WalletTransaction transaction, Transfer transfer) {
        return operationReader.detail(transaction, transfer);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
