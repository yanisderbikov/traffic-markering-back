package ru.trafficmarkering.service.transfer.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.controller.FileController;
import ru.trafficmarkering.dto.transfer.TransferRejectRequestDTO;
import ru.trafficmarkering.dto.transfer.TransferSentRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
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
import ru.trafficmarkering.service.transfer.TransferService;
import ru.trafficmarkering.service.wallet.OperationReader;
import ru.trafficmarkering.service.wallet.WalletLedger;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
class TransferServiceImpl implements TransferService {

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

    @Override
    @Transactional
    public OperationDetailDTO topUp(Long userId, WalletOperationRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        User customer = walletService.requireCustomer(userId);
        Wallet wallet = ledger.lockWallet(customer);
        WalletTransaction transaction = ledger.post(wallet, WalletTransactionType.TOP_UP, request.getAmountKopecks(),
                WalletTransactionStatus.SENT, null, actor, trimToNull(request.getComment()));
        return detail(transaction, sentTransfer(transaction, null, actor, request));
    }

    @Override
    @Transactional
    public OperationDetailDTO withdraw(Long userId, WalletOperationRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        User customer = walletService.requireCustomer(userId);
        String address = requireTronAddress(request.getTronAddress());
        Wallet wallet = ledger.lockWallet(customer);
        WalletTransaction transaction = ledger.post(wallet, WalletTransactionType.WITHDRAWAL, -request.getAmountKopecks(),
                WalletTransactionStatus.SENT, null, actor, trimToNull(request.getComment()));
        return detail(transaction, sentTransfer(transaction, address, actor, request));
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
    public OperationDetailDTO markPayoutSent(Long id, TransferSentRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        WalletTransaction transaction = requireExternal(id);
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
    public OperationDetailDTO reject(Long id, TransferRejectRequestDTO request) {
        User actor = currentUserService.require(Role.FINANCE_MANAGER);
        WalletTransaction transaction = requireExternal(id);
        if (!transaction.getStatus().isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Операция уже закрыта: " + transaction.getStatus().getDescription().toLowerCase());
        }
        Transfer transfer = requireTransfer(transaction);
        ledger.restore(transaction, WalletTransactionStatus.REJECTED);
        transfer.reject(actor, request.getReason().trim());
        saverTransfer.save(transfer);
        return detail(transaction, transfer);
    }

    private Transfer sentTransfer(WalletTransaction transaction, String tronAddress, User actor,
                                  WalletOperationRequestDTO request) {
        Transfer transfer = Transfer.builder().transaction(transaction).tronAddress(tronAddress).build();
        transfer.send(actor, request.getTxId().trim(), requireProofKeys(request.getProofKeys()), null);
        return saverTransfer.save(transfer);
    }

    private WalletTransaction requireExternal(Long id) {
        WalletTransaction transaction = getterWalletTransaction.getByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Операция не найдена: " + id));
        if (!transaction.getType().isExternal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Это внутренняя операция платформы, у неё нет перевода");
        }
        return transaction;
    }

    private Transfer requireTransfer(WalletTransaction transaction) {
        return getterTransfer.getByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Перевод не найден для операции " + transaction.getId()));
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Приложите хотя бы один скриншот перевода");
        }
        for (String key : proofKeys) {
            if (!key.startsWith(FileController.TRANSFER_PROOF_PREFIX + "/") || key.contains("..")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ключ скриншота не из загрузки подтверждений переводов: " + key);
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
