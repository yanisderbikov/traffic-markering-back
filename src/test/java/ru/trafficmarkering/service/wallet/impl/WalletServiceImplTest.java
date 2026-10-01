package ru.trafficmarkering.service.wallet.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverTransfer;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.repository.SaverWalletTransaction;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.storage.FileStorage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceImplTest {

    private final GetterWallet getterWallet = mock(GetterWallet.class);
    private final SaverWallet saverWallet = mock(SaverWallet.class);
    private final GetterWalletTransaction getterWalletTransaction = mock(GetterWalletTransaction.class);
    private final SaverWalletTransaction saverWalletTransaction = mock(SaverWalletTransaction.class);
    private final GetterCampaign getterCampaign = mock(GetterCampaign.class);
    private final GetterCustomerProfile getterCustomerProfile = mock(GetterCustomerProfile.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);

    private final GetterTransfer getterTransfer = mock(GetterTransfer.class);
    private final SaverTransfer saverTransfer = mock(SaverTransfer.class);
    private final FileStorage fileStorage = mock(FileStorage.class);

    private static final String PLATFORM_TRON = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";

    private final WalletServiceImpl service = serviceWith(" " + PLATFORM_TRON + " ");

    private final User customer = User.builder().id(1L).username("customer@traffic.ru").name("Заказчик").role(Role.CUSTOMER).build();
    private final User finance = User.builder().id(2L).username("money@traffic.ru").name("Финансист").role(Role.FINANCE_MANAGER).build();
    private final Wallet wallet = Wallet.builder().id(10L).user(customer).balanceKopecks(1_000_00L).build();

    @BeforeEach
    void setUp() {
        when(saverWallet.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saverWalletTransaction.save(any(WalletTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saverTransfer.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(getterWallet.getByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(getterWallet.getByUserId(1L)).thenReturn(Optional.of(wallet));
        when(getterCampaign.getByCustomerId(1L)).thenReturn(List.of());
        when(getterCustomerProfile.getByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
    }

    private WalletServiceImpl serviceWith(String topUpTronAddress) {
        return new WalletServiceImpl(
                new WalletLedgerImpl(getterWallet, saverWallet, getterWalletTransaction, saverWalletTransaction),
                new OperationReaderImpl(getterTransfer, fileStorage),
                getterWallet, getterWalletTransaction, getterTransfer, saverTransfer, getterCampaign,
                getterCustomerProfile, userRepository, currentUserService, topUpTronAddress);
    }

    @Test
    void blankTopUpAddressMeansNotConfigured() {
        assertThat(serviceWith("  ").topUpTronAddress()).isNull();
    }

    @Test
    void rejectsTopUpAddressThatIsNotTron() {
        assertThatThrownBy(() -> serviceWith("rGG1mB1hpOPxGWD4q6Da6qEVDRkNRw7F8b7u3csbor34ff20phKg7KsBclVY9gyW0y"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOP_UP_TRON_ADDRESS");
    }

    private Campaign campaign(long budgetKopecks) {
        return Campaign.builder().id(UUID.randomUUID()).customer(customer).title("Ролик про кофе")
                .budgetKopecks(budgetKopecks).spentKopecks(0L).build();
    }

    @Test
    void reallocateReservesBudgetIncreaseFromWallet() {
        when(currentUserService.require()).thenReturn(customer);

        service.reallocate(campaign(700_00L), 0L, 700_00L);

        assertThat(wallet.balance()).isEqualTo(300_00L);
        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(saverWalletTransaction).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(WalletTransactionType.ALLOCATION);
        assertThat(saved.getValue().getPublicId()).matches("[A-Z0-9]{8}");
        assertThat(saved.getValue().getAmountKopecks()).isEqualTo(-700_00L);
        assertThat(saved.getValue().getBalanceAfterKopecks()).isEqualTo(300_00L);
        assertThat(saved.getValue().getCampaign()).isNotNull();
    }

    @Test
    void reallocateReturnsBudgetDecreaseToWallet() {
        when(currentUserService.require()).thenReturn(customer);

        service.reallocate(campaign(200_00L), 500_00L, 200_00L);

        assertThat(wallet.balance()).isEqualTo(1_300_00L);
        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(saverWalletTransaction).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(WalletTransactionType.RELEASE);
        assertThat(saved.getValue().getAmountKopecks()).isEqualTo(300_00L);
    }

    @Test
    void reallocateWithSameBudgetTouchesNothing() {
        service.reallocate(campaign(500_00L), 500_00L, 500_00L);

        verify(saverWallet, never()).save(any());
        verify(saverWalletTransaction, never()).save(any());
    }

    @Test
    void reallocateRejectsBudgetAboveFreeBalance() {
        when(currentUserService.require()).thenReturn(customer);

        assertThatThrownBy(() -> service.reallocate(campaign(1_500_00L), 0L, 1_500_00L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(wallet.balance()).isEqualTo(1_000_00L);
        verify(saverWalletTransaction, never()).save(any());
    }

    @Test
    void releaseBeforeDeleteReturnsWholeBudgetWithoutCampaignReference() {
        when(currentUserService.require()).thenReturn(customer);

        service.releaseBeforeDelete(campaign(400_00L));

        assertThat(wallet.balance()).isEqualTo(1_400_00L);
        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(saverWalletTransaction).save(saved.capture());
        assertThat(saved.getValue().getCampaign()).isNull();
        assertThat(saved.getValue().getComment()).contains("Ролик про кофе");
    }

    private WalletTransaction sentWithdrawal() {
        return WalletTransaction.builder().id(7L).publicId("WD000007").wallet(wallet).type(WalletTransactionType.WITHDRAWAL)
                .amountKopecks(-500_00L).balanceAfterKopecks(1_000_00L).status(WalletTransactionStatus.SENT).build();
    }

    @Test
    void confirmClosesWithdrawalSentByFinance() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        WalletTransaction withdrawal = sentWithdrawal();
        Transfer transfer = Transfer.builder().id(70L).transaction(withdrawal).txId("tx").processedBy(finance).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("WD000007")).thenReturn(Optional.of(withdrawal));
        when(getterTransfer.getByTransactionId(7L)).thenReturn(Optional.of(transfer));

        OperationDetailDTO detail = service.confirm("WD000007");

        assertThat(detail.transaction().status()).isEqualTo("CONFIRMED");
        assertThat(detail.transfer().txId()).isEqualTo("tx");
        assertThat(transfer.getConfirmedAt()).isNotNull();
        assertThat(transfer.getClosedAt()).isNotNull();
        assertThat(wallet.balance()).isEqualTo(1_000_00L);

        assertThatThrownBy(() -> service.confirm("WD000007"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmRefusesExpiredWithdrawal() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        WalletTransaction withdrawal = sentWithdrawal();
        Transfer transfer = Transfer.builder().id(70L).transaction(withdrawal).txId("tx")
                .expiresAt(java.time.Instant.now().minusSeconds(1)).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("WD000007")).thenReturn(Optional.of(withdrawal));
        when(getterTransfer.getByTransactionId(7L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.confirm("WD000007"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(withdrawal.getStatus()).isEqualTo(WalletTransactionStatus.SENT);
        assertThat(transfer.getConfirmedAt()).isNull();
    }

    @Test
    void confirmLeavesTopUpsToFinance() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        WalletTransaction topUp = WalletTransaction.builder().id(9L).publicId("TU000009").wallet(wallet).type(WalletTransactionType.TOP_UP)
                .amountKopecks(500_00L).balanceAfterKopecks(1_000_00L).status(WalletTransactionStatus.SENT).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("TU000009")).thenReturn(Optional.of(topUp));

        assertThatThrownBy(() -> service.confirm("TU000009"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wallet.balance()).isEqualTo(1_000_00L);
        verify(saverTransfer, never()).save(any());
    }

    @Test
    void confirmRefusesInternalOperations() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        WalletTransaction allocation = WalletTransaction.builder().id(8L).publicId("AL000008").wallet(wallet)
                .type(WalletTransactionType.ALLOCATION).amountKopecks(-100_00L).balanceAfterKopecks(900_00L)
                .status(WalletTransactionStatus.DONE).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("AL000008")).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> service.confirm("AL000008"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(saverTransfer, never()).save(any());
    }

    @Test
    void confirmHidesForeignOperation() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        User other = User.builder().id(9L).username("other@traffic.ru").name("Другой").role(Role.CUSTOMER).build();
        WalletTransaction foreign = sentWithdrawal();
        foreign.setWallet(Wallet.builder().id(12L).user(other).balanceKopecks(0L).build());
        when(getterWalletTransaction.getByPublicIdForUpdate("WD000007")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.confirm("WD000007"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requireCustomerRejectsUsersWithoutWallet() {
        User creator = User.builder().id(3L).username("creator@traffic.ru").name("Аня").role(Role.CREATOR).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> service.requireCustomer(3L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(service.requireCustomer(1L)).isSameAs(customer);
    }

    @Test
    void operationsShowWhereMoneyWentAndFilterByUserAndType() {
        when(currentUserService.require(Role.FINANCE_MANAGER)).thenReturn(finance);
        Campaign campaign = campaign(300_00L);
        WalletTransaction topUp = WalletTransaction.builder().id(1L).publicId("TU000001").wallet(wallet)
                .type(WalletTransactionType.TOP_UP).amountKopecks(1_000_00L).balanceAfterKopecks(1_000_00L)
                .status(WalletTransactionStatus.DONE).build();
        WalletTransaction allocation = WalletTransaction.builder().id(2L).publicId("AL000002").wallet(wallet)
                .type(WalletTransactionType.ALLOCATION).amountKopecks(-300_00L).balanceAfterKopecks(700_00L)
                .status(WalletTransactionStatus.DONE).campaign(campaign).build();
        User creator = User.builder().id(3L).username("anna@traffic.ru").name("Аня").role(Role.CREATOR).build();
        WalletTransaction earning = WalletTransaction.builder().id(3L).publicId("EA000003")
                .wallet(Wallet.builder().id(11L).user(creator).balanceKopecks(50_00L).build())
                .type(WalletTransactionType.EARNING).amountKopecks(50_00L).balanceAfterKopecks(50_00L)
                .status(WalletTransactionStatus.DONE).campaign(campaign).build();
        when(getterWalletTransaction.getAllWithDetails()).thenReturn(List.of(earning, allocation, topUp));

        List<OperationRowDTO> all = service.operations(null, null, null);
        assertThat(all).extracting(OperationRowDTO::publicId).containsExactly("EA000003", "AL000002", "TU000001");
        assertThat(all.get(2).source().kind()).isEqualTo("EXTERNAL");
        assertThat(all.get(2).destination().label()).isEqualTo("Кошелёк заказчика · Заказчик");
        assertThat(all.get(1).source().label()).isEqualTo("Кошелёк заказчика · Заказчик");
        assertThat(all.get(1).destination().label()).isEqualTo("Объявление «Ролик про кофе»");
        assertThat(all.get(0).source().kind()).isEqualTo("CAMPAIGN");
        assertThat(all.get(0).destination().label()).isEqualTo("Кошелёк криатора · Аня");
        assertThat(all.get(0).ownerName()).isEqualTo("Аня");

        assertThat(service.operations(1L, null, null)).extracting(OperationRowDTO::publicId)
                .containsExactly("AL000002", "TU000001");
        assertThat(service.operations(null, WalletTransactionType.EARNING, null))
                .extracting(OperationRowDTO::publicId).containsExactly("EA000003");
    }

    private void foreignTopUp() {
        User other = User.builder().id(9L).username("other@traffic.ru").name("Другой").role(Role.CUSTOMER).build();
        WalletTransaction foreign = WalletTransaction.builder().id(5L).publicId("TU000005")
                .wallet(Wallet.builder().id(12L).user(other).balanceKopecks(0L).build())
                .type(WalletTransactionType.TOP_UP).amountKopecks(100_00L).balanceAfterKopecks(100_00L)
                .status(WalletTransactionStatus.DONE).build();
        when(getterWalletTransaction.getByPublicIdWithDetails("TU000005")).thenReturn(Optional.of(foreign));
    }

    @Test
    void myOperationHidesForeignTransaction() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        foreignTopUp();

        assertThatThrownBy(() -> service.myOperation("TU000005"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void foreignTransactionIsVisibleToSuperAdminButNotToAdmin() {
        foreignTopUp();
        User admin = User.builder().id(20L).username("admin@traffic.ru").name("Админ").role(Role.ADMIN).build();
        User superAdmin = User.builder().id(21L).username("root@traffic.ru").name("Супер").role(Role.SUPER_ADMIN).build();

        when(currentUserService.require(Role.CUSTOMER)).thenReturn(admin);
        assertThatThrownBy(() -> service.myOperation("TU000005"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(currentUserService.require(Role.CUSTOMER)).thenReturn(superAdmin);
        assertThat(service.myOperation("TU000005").transaction().publicId()).isEqualTo("TU000005");
    }

    @Test
    void superAdminCannotConfirmSomeoneElsesWithdrawal() {
        User superAdmin = User.builder().id(21L).username("root@traffic.ru").name("Супер").role(Role.SUPER_ADMIN).build();
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(superAdmin);
        when(getterWalletTransaction.getByPublicIdForUpdate("WD000007")).thenReturn(Optional.of(sentWithdrawal()));

        assertThatThrownBy(() -> service.confirm("WD000007"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(saverTransfer, never()).save(any());
    }

    @Test
    void myWalletSumsBudgetsAndSpentAcrossCampaigns() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        Campaign first = campaign(300_00L);
        first.setSpentKopecks(120_00L);
        Campaign second = campaign(200_00L);
        when(getterCampaign.getByCustomerId(1L)).thenReturn(List.of(first, second));

        WalletDTO dto = service.myWallet();

        assertThat(dto.balanceKopecks()).isEqualTo(1_000_00L);
        assertThat(dto.allocatedKopecks()).isEqualTo(500_00L);
        assertThat(dto.spentKopecks()).isEqualTo(120_00L);
        assertThat(dto.customerEmail()).isEqualTo("customer@traffic.ru");
        assertThat(dto.topUpTronAddress()).isEqualTo(PLATFORM_TRON);
    }
}
