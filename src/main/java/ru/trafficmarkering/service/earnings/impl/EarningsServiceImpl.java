package ru.trafficmarkering.service.earnings.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.earnings.CreatorWalletDTO;
import ru.trafficmarkering.dto.earnings.PayoutCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverTransfer;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.earnings.EarningsService;
import ru.trafficmarkering.service.wallet.OperationReader;
import ru.trafficmarkering.service.wallet.WalletLedger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
class EarningsServiceImpl implements EarningsService {

    private final WalletLedger ledger;
    private final OperationReader operationReader;
    private final GetterWalletTransaction getterWalletTransaction;
    private final GetterTransfer getterTransfer;
    private final SaverTransfer saverTransfer;
    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public CreatorWalletDTO myWallet() {
        User creator = currentUserService.require(Role.CREATOR);
        Wallet wallet = ledger.walletOf(creator);
        long reserved = 0L;
        long paidOut = 0L;
        long earned = 0L;
        for (WalletTransaction transaction : getterWalletTransaction.getByWalletId(wallet.getId())) {
            if (transaction.getType() == WalletTransactionType.EARNING) {
                earned += transaction.amount();
            } else if (transaction.getType() == WalletTransactionType.PAYOUT) {
                if (transaction.getStatus().isOpen()) {
                    reserved += -transaction.amount();
                } else if (transaction.getStatus() == WalletTransactionStatus.CONFIRMED) {
                    paidOut += -transaction.amount();
                }
            }
        }
        long pending = getterApplication.getByCreatorId(creator.getId()).stream()
                .mapToLong(Application::uncreditedKopecks)
                .filter(delta -> delta > 0)
                .sum();
        return new CreatorWalletDTO(creator.getId(), wallet.balance(), reserved, paidOut, earned, pending,
                wallet.balance() > 0,
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt().toString() : null);
    }

    @Override
    @Transactional
    public List<OperationRowDTO> myOperations() {
        User creator = currentUserService.require(Role.CREATOR);
        Wallet wallet = ledger.walletOf(creator);
        return operationReader.rows(getterWalletTransaction.getByWalletId(wallet.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OperationDetailDTO myOperation(Long id) {
        User creator = currentUserService.require(Role.CREATOR);
        WalletTransaction transaction = requireOwn(id, creator);
        return detail(transaction);
    }

    @Override
    @Transactional
    public OperationDetailDTO requestPayout(PayoutCreateRequestDTO request) {
        User creator = currentUserService.require(Role.CREATOR);
        long amount = request.getAmountKopecks();
        String address = request.getTronAddress().trim();
        if (!Transfer.isTronAddress(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Адрес не похож на кошелёк TRON (TRC-20): он начинается с T и состоит из 34 символов");
        }
        Wallet wallet = ledger.lockWallet(creator);
        WalletTransaction transaction = ledger.post(wallet, WalletTransactionType.PAYOUT, -amount,
                WalletTransactionStatus.PENDING, null, creator, "USDT TRC-20 → " + address);
        Transfer transfer = saverTransfer.save(Transfer.builder()
                .transaction(transaction)
                .tronAddress(address)
                .build());
        return operationReader.detail(transaction, transfer);
    }

    @Override
    @Transactional
    public OperationDetailDTO confirmPayout(Long id) {
        User creator = currentUserService.require(Role.CREATOR);
        WalletTransaction transaction = requireOwnPayout(id, creator);
        if (transaction.getStatus() != WalletTransactionStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Подтвердить можно только выплату, которую финансист уже отправил");
        }
        Transfer transfer = requireTransfer(transaction);
        transfer.confirm();
        saverTransfer.save(transfer);
        transaction.setStatus(WalletTransactionStatus.CONFIRMED);
        return detail(transaction);
    }

    @Override
    @Transactional
    public OperationDetailDTO cancelPayout(Long id) {
        User creator = currentUserService.require(Role.CREATOR);
        WalletTransaction transaction = requireOwnPayout(id, creator);
        if (transaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отменить можно только заявку, которую финансист ещё не отправил");
        }
        Transfer transfer = requireTransfer(transaction);
        ledger.restore(transaction, WalletTransactionStatus.CANCELLED);
        transfer.cancel();
        saverTransfer.save(transfer);
        return detail(transaction);
    }

    @Override
    @Transactional
    public int creditAccrued() {
        Map<Long, List<Application>> byCreator = getterApplication.getCreditable().stream()
                .collect(Collectors.groupingBy(application -> application.getCreator().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        int credited = 0;
        int held = 0;
        for (List<Application> applications : byCreator.values()) {
            User creator = applications.get(0).getCreator();
            Map<UUID, Long> accruedByCampaign = accruedByCampaign(creator);
            Wallet wallet = null;
            for (Application application : applications) {
                long delta = application.uncreditedKopecks();
                if (delta <= 0) {
                    continue;
                }
                Campaign campaign = application.getCampaign();
                if (accruedByCampaign.getOrDefault(campaign.getId(), 0L) < campaign.minPayout()) {
                    held++;
                    continue;
                }
                if (wallet == null) {
                    wallet = ledger.lockWallet(creator);
                }
                ledger.post(wallet, WalletTransactionType.EARNING, delta, WalletTransactionStatus.DONE,
                        campaign, null, campaign.getTitle());
                application.setCreditedKopecks(application.getAccruedKopecks());
                saverApplication.save(application);
                credited++;
            }
        }
        log.info("Начисления в кошельки криаторов: откликов {}, криаторов {}, ждут порога вывода {}",
                credited, byCreator.size(), held);
        return credited;
    }

    private Map<UUID, Long> accruedByCampaign(User creator) {
        return getterApplication.getByCreatorId(creator.getId()).stream()
                .collect(Collectors.groupingBy(application -> application.getCampaign().getId(),
                        Collectors.summingLong(Application::accrued)));
    }

    private WalletTransaction requireOwn(Long id, User creator) {
        WalletTransaction transaction = getterWalletTransaction.getByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Операция не найдена: " + id));
        Long ownerId = transaction.getWallet().getUser().getId();
        if (!creator.getRole().isAdmin() && !Objects.equals(ownerId, creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужая операция");
        }
        return transaction;
    }

    private WalletTransaction requireOwnPayout(Long id, User creator) {
        WalletTransaction transaction = requireOwn(id, creator);
        if (transaction.getType() != WalletTransactionType.PAYOUT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Это не заявка на вывод");
        }
        return transaction;
    }

    private Transfer requireTransfer(WalletTransaction transaction) {
        return getterTransfer.getByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Перевод не найден для операции " + transaction.getId()));
    }

    private OperationDetailDTO detail(WalletTransaction transaction) {
        return operationReader.detail(transaction);
    }
}
