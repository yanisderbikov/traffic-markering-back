package ru.trafficmarkering.service.wallet.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.CampaignTotals;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverTransfer;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.wallet.OperationReader;
import ru.trafficmarkering.service.wallet.WalletLedger;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class WalletServiceImpl implements WalletService {

    private static final Set<Role> CUSTOMER_WALLET_ROLES = EnumSet.of(Role.CUSTOMER, Role.ADMIN, Role.SUPER_ADMIN);

    private final WalletLedger ledger;
    private final OperationReader operationReader;
    private final GetterWallet getterWallet;
    private final GetterWalletTransaction getterWalletTransaction;
    private final GetterTransfer getterTransfer;
    private final SaverTransfer saverTransfer;
    private final GetterCampaign getterCampaign;
    private final GetterCustomerProfile getterCustomerProfile;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final String topUpTronAddress;

    WalletServiceImpl(WalletLedger ledger,
                      OperationReader operationReader,
                      GetterWallet getterWallet,
                      GetterWalletTransaction getterWalletTransaction,
                      GetterTransfer getterTransfer,
                      SaverTransfer saverTransfer,
                      GetterCampaign getterCampaign,
                      GetterCustomerProfile getterCustomerProfile,
                      UserRepository userRepository,
                      CurrentUserService currentUserService,
                      @Value("${wallet.top-up-tron-address}") String topUpTronAddress) {
        this.ledger = ledger;
        this.operationReader = operationReader;
        this.getterWallet = getterWallet;
        this.getterWalletTransaction = getterWalletTransaction;
        this.getterTransfer = getterTransfer;
        this.saverTransfer = saverTransfer;
        this.getterCampaign = getterCampaign;
        this.getterCustomerProfile = getterCustomerProfile;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.topUpTronAddress = trimToNull(topUpTronAddress);
    }

    @Override
    @Transactional
    public WalletDTO myWallet() {
        User customer = currentUserService.require(Role.CUSTOMER);
        return toDTO(ledger.walletOf(customer), customer);
    }

    @Override
    @Transactional
    public List<OperationRowDTO> myOperations() {
        User customer = currentUserService.require(Role.CUSTOMER);
        Wallet wallet = ledger.walletOf(customer);
        return operationReader.rows(getterWalletTransaction.getByWalletId(wallet.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OperationDetailDTO myOperation(Long id) {
        User customer = currentUserService.require(Role.CUSTOMER);
        return operationReader.detail(requireOwn(id, customer));
    }

    @Override
    @Transactional
    public OperationDetailDTO confirm(Long id) {
        User customer = currentUserService.require(Role.CUSTOMER);
        WalletTransaction transaction = requireOwn(id, customer);
        if (transaction.getType() != WalletTransactionType.TOP_UP
                && transaction.getType() != WalletTransactionType.WITHDRAWAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Подтверждают только пополнение и вывод");
        }
        if (transaction.getStatus() != WalletTransactionStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Подтвердить можно только операцию, которая ждёт подтверждения, сейчас она "
                            + transaction.getStatus().getDescription().toLowerCase());
        }
        Transfer transfer = getterTransfer.getByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Перевод не найден для операции " + transaction.getId()));
        transfer.confirm();
        saverTransfer.save(transfer);
        transaction.setStatus(WalletTransactionStatus.CONFIRMED);
        return operationReader.detail(transaction, transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletDTO> customers() {
        currentUserService.require(Role.FINANCE_MANAGER);
        List<Wallet> wallets = getterWallet.getAllWithUserByRoles(CUSTOMER_WALLET_ROLES);
        Map<Long, CampaignTotals> totals = getterCampaign.getTotalsByCustomer().stream()
                .collect(Collectors.toMap(CampaignTotals::customerId, Function.identity()));
        Set<Long> userIds = wallets.stream().map(wallet -> wallet.getUser().getId()).collect(Collectors.toSet());
        Map<Long, CustomerProfile> profiles = getterCustomerProfile.getAllByUserIds(userIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));
        return wallets.stream()
                .map(wallet -> {
                    Long userId = wallet.getUser().getId();
                    CampaignTotals total = totals.get(userId);
                    return WalletDTO.from(wallet, wallet.getUser(), profiles.get(userId),
                            total != null ? total.budget() : 0L,
                            total != null ? total.spent() : 0L,
                            topUpTronAddress);
                })
                .toList();
    }

    @Override
    @Transactional
    public WalletDTO customer(Long userId) {
        currentUserService.require(Role.FINANCE_MANAGER);
        User customer = requireCustomer(userId);
        return toDTO(ledger.walletOf(customer), customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationRowDTO> operations(Long userId, WalletTransactionType type, WalletTransactionStatus status) {
        currentUserService.require(Role.FINANCE_MANAGER);
        List<WalletTransaction> transactions = getterWalletTransaction.getAllWithDetails().stream()
                .filter(transaction -> userId == null
                        || Objects.equals(transaction.getWallet().getUser().getId(), userId))
                .filter(transaction -> type == null || transaction.getType() == type)
                .filter(transaction -> status == null || transaction.getStatus() == status)
                .toList();
        return operationReader.rows(transactions);
    }

    @Override
    @Transactional(readOnly = true)
    public OperationDetailDTO operation(Long id) {
        currentUserService.require(Role.FINANCE_MANAGER);
        return operationReader.detail(requireTransaction(id));
    }

    @Override
    @Transactional
    public void reallocate(Campaign campaign, long previousBudgetKopecks, long nextBudgetKopecks) {
        long delta = nextBudgetKopecks - previousBudgetKopecks;
        if (delta == 0) {
            return;
        }
        User actor = currentUserService.require();
        Wallet wallet = ledger.lockWallet(campaign.getCustomer());
        WalletTransactionType type = delta > 0 ? WalletTransactionType.ALLOCATION : WalletTransactionType.RELEASE;
        ledger.post(wallet, type, -delta, WalletTransactionStatus.DONE, campaign, actor, campaign.getTitle());
    }

    @Override
    @Transactional
    public void releaseBeforeDelete(Campaign campaign) {
        long budget = campaign.getBudgetKopecks() != null ? campaign.getBudgetKopecks() : 0L;
        if (budget <= 0) {
            return;
        }
        User actor = currentUserService.require();
        Wallet wallet = ledger.lockWallet(campaign.getCustomer());
        ledger.post(wallet, WalletTransactionType.RELEASE, budget, WalletTransactionStatus.DONE, null, actor,
                "Удалено объявление «" + campaign.getTitle() + "»");
    }

    private WalletTransaction requireTransaction(Long id) {
        return getterWalletTransaction.getByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Операция не найдена: " + id));
    }

    private WalletTransaction requireOwn(Long id, User customer) {
        WalletTransaction transaction = requireTransaction(id);
        Long ownerId = transaction.getWallet().getUser().getId();
        if (!customer.getRole().isAdmin() && !Objects.equals(ownerId, customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужая операция");
        }
        return transaction;
    }

    @Override
    @Transactional(readOnly = true)
    public User requireCustomer(Long userId) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказчик не найден: " + userId);
        }
        if (!user.getRole().implies(Role.CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Кошелёк заказчика есть только у заказчиков, а у " + user.getUsername() + " роль "
                            + user.getRole().getDescription().toLowerCase());
        }
        return user;
    }

    private WalletDTO toDTO(Wallet wallet, User customer) {
        List<Campaign> campaigns = getterCampaign.getByCustomerId(customer.getId());
        long allocated = campaigns.stream()
                .mapToLong(campaign -> campaign.getBudgetKopecks() != null ? campaign.getBudgetKopecks() : 0L)
                .sum();
        long spent = campaigns.stream()
                .mapToLong(campaign -> campaign.getSpentKopecks() != null ? campaign.getSpentKopecks() : 0L)
                .sum();
        CustomerProfile profile = getterCustomerProfile.getByUserId(customer.getId()).orElse(null);
        return WalletDTO.from(wallet, customer, profile, allocated, spent, topUpTronAddress);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
