package ru.trafficmarkering.service.earnings.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.earnings.CreatorWalletDTO;
import ru.trafficmarkering.dto.earnings.PayoutCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverTransfer;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.repository.SaverWalletTransaction;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.wallet.WalletLedger;
import ru.trafficmarkering.service.wallet.impl.WalletLedgerTestSupport;

import java.time.Instant;
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

class EarningsServiceImplTest {

    private static final String TRON = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";

    private final GetterWallet getterWallet = mock(GetterWallet.class);
    private final SaverWallet saverWallet = mock(SaverWallet.class);
    private final GetterWalletTransaction getterWalletTransaction = mock(GetterWalletTransaction.class);
    private final SaverWalletTransaction saverWalletTransaction = mock(SaverWalletTransaction.class);
    private final GetterTransfer getterTransfer = mock(GetterTransfer.class);
    private final SaverTransfer saverTransfer = mock(SaverTransfer.class);
    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final SaverApplication saverApplication = mock(SaverApplication.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final FileStorage fileStorage = mock(FileStorage.class);

    private final WalletLedger ledger = WalletLedgerTestSupport.ledger(getterWallet, saverWallet, saverWalletTransaction);
    private final EarningsServiceImpl service = new EarningsServiceImpl(ledger,
            WalletLedgerTestSupport.reader(getterTransfer, fileStorage), getterWalletTransaction,
            getterTransfer, saverTransfer, getterApplication, saverApplication, currentUserService);

    private final User creator = User.builder().id(1L).username("anna@traffic.ru").name("Аня").role(Role.CREATOR).build();
    private final Wallet wallet = Wallet.builder().id(10L).user(creator).balanceKopecks(7_000_00L).build();

    @BeforeEach
    void setUp() {
        when(saverWallet.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saverWalletTransaction.save(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction transaction = inv.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(100L);
            }
            return transaction;
        });
        when(saverTransfer.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saverApplication.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(getterWallet.getByUserId(1L)).thenReturn(Optional.of(wallet));
        when(getterWallet.getByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(fileStorage.presignedUrl(any())).thenAnswer(inv -> "https://s3/" + inv.getArgument(0));
    }

    private WalletTransaction payout(long amount, WalletTransactionStatus status) {
        return WalletTransaction.builder().id(100L).wallet(wallet).type(WalletTransactionType.PAYOUT)
                .amountKopecks(-amount).balanceAfterKopecks(wallet.balance()).status(status)
                .comment("USDT TRC-20 → " + TRON).createdAt(Instant.now()).build();
    }

    @Test
    void requestPayoutReservesMoneyAndCreatesPendingRequest() {
        OperationDetailDTO detail = service.requestPayout(
                PayoutCreateRequestDTO.builder().amountKopecks(5_000_00L).tronAddress(" " + TRON + " ").build());

        assertThat(wallet.balance()).isEqualTo(2_000_00L);
        assertThat(detail.transaction().type()).isEqualTo("PAYOUT");
        assertThat(detail.transaction().status()).isEqualTo("PENDING");
        assertThat(detail.transaction().amountKopecks()).isEqualTo(-5_000_00L);
        assertThat(detail.transfer().tronAddress()).isEqualTo(TRON);
        assertThat(detail.transaction().source().label()).isEqualTo("Кошелёк криатора · Аня");
        assertThat(detail.transaction().destination().label()).isEqualTo("TRON · " + TRON);
        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(saverTransfer).save(saved.capture());
        assertThat(saved.getValue().getTransaction().getStatus()).isEqualTo(WalletTransactionStatus.PENDING);
    }

    @Test
    void requestPayoutHasNoPlatformWideMinimum() {
        OperationDetailDTO detail = service.requestPayout(
                PayoutCreateRequestDTO.builder().amountKopecks(1_00L).tronAddress(TRON).build());

        assertThat(detail.transaction().amountKopecks()).isEqualTo(-1_00L);
        assertThat(wallet.balance()).isEqualTo(6_999_00L);
    }

    @Test
    void requestPayoutRejectsNonTronAddress() {
        assertThatThrownBy(() -> service.requestPayout(
                PayoutCreateRequestDTO.builder().amountKopecks(5_000_00L).tronAddress("0x1234").build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TRON");
        verify(saverWalletTransaction, never()).save(any());
    }

    @Test
    void requestPayoutRejectsMoreThanAvailable() {
        assertThatThrownBy(() -> service.requestPayout(
                PayoutCreateRequestDTO.builder().amountKopecks(7_000_01L).tronAddress(TRON).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(wallet.balance()).isEqualTo(7_000_00L);
    }

    @Test
    void cancelPayoutReturnsMoneyOnlyWhilePending() {
        WalletTransaction pending = payout(5_000_00L, WalletTransactionStatus.PENDING);
        wallet.setBalanceKopecks(2_000_00L);
        when(getterWalletTransaction.getByIdWithDetails(100L)).thenReturn(Optional.of(pending));
        when(getterTransfer.getByTransactionId(100L))
                .thenReturn(Optional.of(Transfer.builder().transaction(pending).tronAddress(TRON).build()));

        OperationDetailDTO detail = service.cancelPayout(100L);

        assertThat(detail.transaction().status()).isEqualTo("CANCELLED");
        assertThat(wallet.balance()).isEqualTo(7_000_00L);

        assertThatThrownBy(() -> service.cancelPayout(100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmPayoutRequiresSentStatus() {
        WalletTransaction pending = payout(5_000_00L, WalletTransactionStatus.PENDING);
        when(getterWalletTransaction.getByIdWithDetails(100L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirmPayout(100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        pending.setStatus(WalletTransactionStatus.SENT);
        Transfer transfer = Transfer.builder().transaction(pending).tronAddress(TRON).build();
        when(getterTransfer.getByTransactionId(100L)).thenReturn(Optional.of(transfer));

        OperationDetailDTO detail = service.confirmPayout(100L);

        assertThat(detail.transaction().status()).isEqualTo("CONFIRMED");
        assertThat(transfer.getConfirmedAt()).isNotNull();
        assertThat(transfer.getClosedAt()).isNotNull();
    }

    @Test
    void foreignOperationIsForbidden() {
        User other = User.builder().id(2L).username("other@traffic.ru").name("Кто-то").role(Role.CREATOR).build();
        WalletTransaction foreign = payout(5_000_00L, WalletTransactionStatus.PENDING);
        foreign.setWallet(Wallet.builder().id(11L).user(other).balanceKopecks(0L).build());
        when(getterWalletTransaction.getByIdWithDetails(100L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.myOperation(100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void myWalletSplitsReservedPaidOutEarnedAndPending() {
        when(getterWalletTransaction.getByWalletId(10L)).thenReturn(List.of(
                WalletTransaction.builder().type(WalletTransactionType.EARNING).amountKopecks(9_000_00L)
                        .status(WalletTransactionStatus.DONE).build(),
                WalletTransaction.builder().type(WalletTransactionType.EARNING).amountKopecks(3_000_00L)
                        .status(WalletTransactionStatus.DONE).build(),
                payout(5_000_00L, WalletTransactionStatus.SENT),
                payout(5_000_00L, WalletTransactionStatus.CONFIRMED),
                payout(5_000_00L, WalletTransactionStatus.REJECTED)));
        Campaign campaign = campaign(3_000_00L);
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(
                application(campaign, 1_500_00L, 0L),
                application(campaign, 700_00L, 700_00L),
                application(campaign, 200_00L, 300_00L)));

        CreatorWalletDTO dto = service.myWallet();

        assertThat(dto.balanceKopecks()).isEqualTo(7_000_00L);
        assertThat(dto.earnedKopecks()).isEqualTo(12_000_00L);
        assertThat(dto.reservedKopecks()).isEqualTo(5_000_00L);
        assertThat(dto.paidOutKopecks()).isEqualTo(5_000_00L);
        assertThat(dto.pendingKopecks()).isEqualTo(1_500_00L);
        assertThat(dto.payoutAvailable()).isTrue();
    }

    @Test
    void myWalletOffersPayoutOnlyWithBalance() {
        wallet.setBalanceKopecks(0L);

        assertThat(service.myWallet().payoutAvailable()).isFalse();
    }

    @Test
    void creditAccruedMovesOnlyUncreditedDeltaIntoWallet() {
        Campaign campaign = campaign(2_000_00L);
        Application fresh = application(campaign, 1_200_00L, 0L);
        Application partly = application(campaign, 800_00L, 500_00L);
        when(getterApplication.getCreditable()).thenReturn(List.of(fresh, partly));
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(fresh, partly));

        int credited = service.creditAccrued();

        assertThat(credited).isEqualTo(2);
        assertThat(wallet.balance()).isEqualTo(7_000_00L + 1_200_00L + 300_00L);
        assertThat(fresh.getCreditedKopecks()).isEqualTo(1_200_00L);
        assertThat(partly.getCreditedKopecks()).isEqualTo(800_00L);
        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(saverWalletTransaction, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(transaction -> {
            assertThat(transaction.getType()).isEqualTo(WalletTransactionType.EARNING);
            assertThat(transaction.getStatus()).isEqualTo(WalletTransactionStatus.DONE);
            assertThat(transaction.getCampaign()).isSameAs(campaign);
        });
    }

    @Test
    void creditAccruedSkipsApplicationsWithoutNewMoney() {
        Campaign campaign = campaign(100_00L);
        Application settled = application(campaign, 500_00L, 500_00L);
        when(getterApplication.getCreditable()).thenReturn(List.of(settled));
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(settled));

        assertThat(service.creditAccrued()).isZero();
        verify(saverWalletTransaction, never()).save(any());
    }

    @Test
    void creditAccruedHoldsCampaignMoneyBelowItsThreshold() {
        Campaign strict = campaign(3_000_00L);
        Campaign lenient = campaign(1_000_00L);
        Application heldOne = application(strict, 1_200_00L, 0L);
        Application heldTwo = application(strict, 800_00L, 0L);
        Application released = application(lenient, 1_500_00L, 0L);
        when(getterApplication.getCreditable()).thenReturn(List.of(heldOne, heldTwo, released));
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(heldOne, heldTwo, released));

        int credited = service.creditAccrued();

        assertThat(credited).isEqualTo(1);
        assertThat(wallet.balance()).isEqualTo(7_000_00L + 1_500_00L);
        assertThat(released.getCreditedKopecks()).isEqualTo(1_500_00L);
        assertThat(heldOne.getCreditedKopecks()).isZero();
        assertThat(heldTwo.getCreditedKopecks()).isZero();
        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(saverWalletTransaction).save(saved.capture());
        assertThat(saved.getValue().getCampaign()).isSameAs(lenient);
    }

    @Test
    void creditAccruedSumsCreatorApplicationsAcrossCampaign() {
        Campaign campaign = campaign(3_000_00L);
        Application first = application(campaign, 1_600_00L, 0L);
        Application second = application(campaign, 1_500_00L, 0L);
        when(getterApplication.getCreditable()).thenReturn(List.of(first, second));
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(first, second));

        assertThat(service.creditAccrued()).isEqualTo(2);
        assertThat(wallet.balance()).isEqualTo(7_000_00L + 3_100_00L);
    }

    @Test
    void creditAccruedDoesNotTouchWalletWhenEverythingIsHeld() {
        Campaign campaign = campaign(3_000_00L);
        Application held = application(campaign, 2_999_99L, 0L);
        when(getterApplication.getCreditable()).thenReturn(List.of(held));
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(held));

        assertThat(service.creditAccrued()).isZero();
        assertThat(wallet.balance()).isEqualTo(7_000_00L);
        verify(getterWallet, never()).getByUserIdForUpdate(any());
        verify(saverApplication, never()).save(any());
    }

    private Campaign campaign(long minPayoutKopecks) {
        return Campaign.builder().id(UUID.randomUUID()).title("Ролик про кофе").minPayoutKopecks(minPayoutKopecks).build();
    }

    private Application application(Campaign campaign, long accruedKopecks, long creditedKopecks) {
        return Application.builder().id(UUID.randomUUID()).campaign(campaign).creator(creator)
                .status(ApplicationStatus.APPROVED).accruedKopecks(accruedKopecks).creditedKopecks(creditedKopecks).build();
    }
}
