package ru.trafficmarkering.service.transfer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverTransfer;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.repository.SaverWalletTransaction;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.wallet.WalletService;
import ru.trafficmarkering.service.wallet.impl.WalletLedgerTestSupport;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceImplTest {

    private static final String TRON = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";
    private static final String PLATFORM_TRON = "TPlatformAddress00000000000000000000";
    private static final List<String> PROOFS = List.of("transfer-proofs/1.png");

    private final GetterWallet getterWallet = mock(GetterWallet.class);
    private final SaverWallet saverWallet = mock(SaverWallet.class);
    private final GetterWalletTransaction getterWalletTransaction = mock(GetterWalletTransaction.class);
    private final SaverWalletTransaction saverWalletTransaction = mock(SaverWalletTransaction.class);
    private final GetterTransfer getterTransfer = mock(GetterTransfer.class);
    private final SaverTransfer saverTransfer = mock(SaverTransfer.class);
    private final WalletService walletService = mock(WalletService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final FileStorage fileStorage = mock(FileStorage.class);

    private final TransferServiceImpl service = new TransferServiceImpl(
            WalletLedgerTestSupport.ledger(getterWallet, saverWallet, saverWalletTransaction),
            WalletLedgerTestSupport.reader(getterTransfer, fileStorage),
            getterWalletTransaction, getterTransfer, saverTransfer, walletService, currentUserService);

    private final User creator = User.builder().id(1L).username("anna@traffic.ru").name("Аня").role(Role.CREATOR).build();
    private final User customer = User.builder().id(3L).username("customer@traffic.ru").name("Заказчик").role(Role.CUSTOMER).build();
    private final User finance = User.builder().id(2L).username("money@traffic.ru").name("Маша").role(Role.FINANCE_MANAGER).build();
    private final Wallet creatorWallet = Wallet.builder().id(10L).user(creator).balanceKopecks(2_000_00L).build();
    private final Wallet customerWallet = Wallet.builder().id(11L).user(customer).balanceKopecks(1_000_00L).build();
    private final WalletTransaction pending = WalletTransaction.builder().id(100L).wallet(creatorWallet)
            .type(WalletTransactionType.PAYOUT).amountKopecks(-5_000_00L).balanceAfterKopecks(2_000_00L)
            .status(WalletTransactionStatus.PENDING).createdAt(Instant.now()).build();
    private final Transfer payout = Transfer.builder().id(7L).transaction(pending).tronAddress(TRON).build();

    @BeforeEach
    void setUp() {
        when(currentUserService.require(Role.FINANCE_MANAGER)).thenReturn(finance);
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        when(walletService.topUpTronAddress()).thenReturn(PLATFORM_TRON);
        when(walletService.requireCustomer(3L)).thenReturn(customer);
        when(saverWallet.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saverWalletTransaction.save(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction transaction = inv.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(200L);
            }
            return transaction;
        });
        when(saverTransfer.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(getterWallet.getByUserIdForUpdate(1L)).thenReturn(Optional.of(creatorWallet));
        when(getterWallet.getByUserIdForUpdate(3L)).thenReturn(Optional.of(customerWallet));
        when(getterWallet.getByUserId(3L)).thenReturn(Optional.of(customerWallet));
        when(getterWallet.getByIdForUpdate(10L)).thenReturn(Optional.of(creatorWallet));
        when(getterWallet.getByIdForUpdate(11L)).thenReturn(Optional.of(customerWallet));
        when(getterWalletTransaction.getByIdForUpdate(100L)).thenReturn(Optional.of(pending));
        when(getterTransfer.getByTransactionId(100L)).thenReturn(Optional.of(payout));
        when(fileStorage.presignedUrl(any())).thenAnswer(inv -> "https://s3/" + inv.getArgument(0));
    }

    private WalletOperationRequestDTO.WalletOperationRequestDTOBuilder operation(long amountKopecks) {
        return WalletOperationRequestDTO.builder().amountKopecks(amountKopecks).txId(" tx-1 ").proofKeys(PROOFS);
    }

    private WalletTransaction topUp(WalletTransactionStatus status) {
        WalletTransaction topUp = WalletTransaction.builder().id(300L).wallet(customerWallet)
                .type(WalletTransactionType.TOP_UP).amountKopecks(800_00L).balanceAfterKopecks(1_000_00L)
                .status(status).actor(customer).createdAt(Instant.now()).build();
        when(getterWalletTransaction.getByIdForUpdate(300L)).thenReturn(Optional.of(topUp));
        return topUp;
    }

    private Transfer topUpTransfer(WalletTransaction topUp) {
        Transfer transfer = Transfer.builder().id(8L).transaction(topUp).tronAddress(PLATFORM_TRON).build();
        when(getterTransfer.getByTransactionId(300L)).thenReturn(Optional.of(transfer));
        return transfer;
    }

    @Test
    void requestTopUpGivesPlatformAddressAndLeavesBalanceAlone() {
        OperationDetailDTO detail = service.requestTopUp(TopUpCreateRequestDTO.builder().amountKopecks(2_500_00L).build());

        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);
        assertThat(detail.transaction().type()).isEqualTo("TOP_UP");
        assertThat(detail.transaction().status()).isEqualTo("PENDING");
        assertThat(detail.transaction().amountKopecks()).isEqualTo(2_500_00L);
        assertThat(detail.transaction().actorName()).isEqualTo("Заказчик");
        assertThat(detail.transfer().tronAddress()).isEqualTo(PLATFORM_TRON);
        assertThat(detail.transfer().proofs()).isEmpty();
        assertThat(detail.transfer().sentAt()).isNull();
    }

    @Test
    void requestTopUpNeedsConfiguredAddress() {
        when(walletService.topUpTronAddress()).thenReturn(null);

        assertThatThrownBy(() -> service.requestTopUp(TopUpCreateRequestDTO.builder().amountKopecks(100_00L).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(saverWalletTransaction, never()).save(any());
    }

    @Test
    void markTopUpPaidAttachesProofsAndWaitsForFinance() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.PENDING);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.markTopUpPaid(300L, TopUpPaidRequestDTO.builder()
                .txId("  ").proofKeys(List.of("transfer-proofs/1.png", " transfer-proofs/2.pdf ")).build());

        assertThat(detail.transaction().status()).isEqualTo("SENT");
        assertThat(detail.transfer().txId()).isNull();
        assertThat(detail.transfer().proofs()).extracting("key")
                .containsExactly("transfer-proofs/1.png", "transfer-proofs/2.pdf");
        assertThat(detail.transfer().processedByName()).isNull();
        assertThat(transfer.getSentAt()).isNotNull();
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);

        assertThatThrownBy(() -> service.markTopUpPaid(300L, TopUpPaidRequestDTO.builder().proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markTopUpPaidRejectsForeignProofKeys() {
        topUpTransfer(topUp(WalletTransactionStatus.PENDING));

        assertThatThrownBy(() -> service.markTopUpPaid(300L,
                TopUpPaidRequestDTO.builder().proofKeys(List.of("campaign-photos/x.png")).build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ключ файла");
    }

    @Test
    void markTopUpPaidRejectsForeignRequest() {
        WalletTransaction foreign = topUp(WalletTransactionStatus.PENDING);
        foreign.setWallet(creatorWallet);

        assertThatThrownBy(() -> service.markTopUpPaid(300L, TopUpPaidRequestDTO.builder().proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelTopUpOnlyBeforePayment() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.PENDING);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.cancelTopUp(300L);

        assertThat(detail.transaction().status()).isEqualTo("CANCELLED");
        assertThat(transfer.getClosedAt()).isNotNull();

        topUp.setStatus(WalletTransactionStatus.SENT);
        assertThatThrownBy(() -> service.cancelTopUp(300L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmTopUpCreditsBalanceOnce() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.SENT);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.confirmTopUp(300L);

        assertThat(customerWallet.balance()).isEqualTo(1_800_00L);
        assertThat(detail.transaction().status()).isEqualTo("CONFIRMED");
        assertThat(detail.transaction().balanceAfterKopecks()).isEqualTo(1_800_00L);
        assertThat(detail.transfer().processedByName()).isEqualTo("Маша");
        assertThat(transfer.getConfirmedAt()).isNotNull();

        assertThatThrownBy(() -> service.confirmTopUp(300L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(customerWallet.balance()).isEqualTo(1_800_00L);
    }

    @Test
    void confirmTopUpRefusesPayouts() {
        assertThatThrownBy(() -> service.confirmTopUp(100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(creatorWallet.balance()).isEqualTo(2_000_00L);
    }

    @Test
    void withdrawSendsToCustomerTronAddress() {
        OperationDetailDTO detail = service.withdraw(3L, operation(400_00L).tronAddress(" " + TRON + " ").build());

        assertThat(customerWallet.balance()).isEqualTo(600_00L);
        assertThat(detail.transaction().type()).isEqualTo("WITHDRAWAL");
        assertThat(detail.transaction().status()).isEqualTo("SENT");
        assertThat(detail.transaction().destination().label()).isEqualTo("TRON · " + TRON);
        assertThat(detail.transfer().tronAddress()).isEqualTo(TRON);
    }

    @Test
    void withdrawNeedsTronAddress() {
        assertThatThrownBy(() -> service.withdraw(3L, operation(400_00L).tronAddress("0x1234").build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TRON");
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);
        verify(saverWalletTransaction, never()).save(any());
    }

    @Test
    void withdrawIsLimitedByFreeBalance() {
        assertThatThrownBy(() -> service.withdraw(3L, operation(1_000_01L).tronAddress(TRON).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);
    }

    @Test
    void markPayoutSentStoresTxIdProofsAndMovesToSent() {
        OperationDetailDTO detail = service.markPayoutSent(100L, TransferSentRequestDTO.builder()
                .txId(" abc123 ")
                .comment("  https://tronscan.org/#/transaction/abc  ")
                .proofKeys(List.of("transfer-proofs/1.png", " transfer-proofs/2.png "))
                .build());

        assertThat(detail.transaction().status()).isEqualTo("SENT");
        assertThat(detail.transfer().txId()).isEqualTo("abc123");
        assertThat(detail.transfer().financeComment()).isEqualTo("https://tronscan.org/#/transaction/abc");
        assertThat(detail.transfer().proofs()).extracting("url")
                .containsExactly("https://s3/transfer-proofs/1.png", "https://s3/transfer-proofs/2.png");
        assertThat(detail.transfer().processedByName()).isEqualTo("Маша");
        assertThat(payout.getSentAt()).isNotNull();
        assertThat(creatorWallet.balance()).isEqualTo(2_000_00L);
    }

    @Test
    void markPayoutSentNeedsProofs() {
        assertThatThrownBy(() -> service.markPayoutSent(100L,
                TransferSentRequestDTO.builder().txId("abc").proofKeys(List.of("  ")).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pending.getStatus()).isEqualTo(WalletTransactionStatus.PENDING);
    }

    @Test
    void markPayoutSentOnlyFromPending() {
        pending.setStatus(WalletTransactionStatus.SENT);

        assertThatThrownBy(() -> service.markPayoutSent(100L,
                TransferSentRequestDTO.builder().txId("abc").proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectPayoutReturnsMoneyAndKeepsReason() {
        OperationDetailDTO detail = service.reject(100L,
                TransferRejectRequestDTO.builder().reason(" Адрес не TRC-20 ").build());

        assertThat(detail.transaction().status()).isEqualTo("REJECTED");
        assertThat(detail.transfer().rejectReason()).isEqualTo("Адрес не TRC-20");
        assertThat(creatorWallet.balance()).isEqualTo(7_000_00L);
        assertThat(payout.getClosedAt()).isNotNull();

        assertThatThrownBy(() -> service.reject(100L, TransferRejectRequestDTO.builder().reason("ещё раз").build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(creatorWallet.balance()).isEqualTo(7_000_00L);
    }

    @Test
    void rejectTopUpClosesRequestWithoutTouchingBalance() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.SENT);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.reject(300L, TransferRejectRequestDTO.builder().reason("Перевод не пришёл").build());

        assertThat(detail.transaction().status()).isEqualTo("REJECTED");
        assertThat(detail.transfer().rejectReason()).isEqualTo("Перевод не пришёл");
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);
        assertThat(transfer.getProcessedBy()).isSameAs(finance);
    }

    @Test
    void rejectRefusesInternalOperations() {
        WalletTransaction allocation = WalletTransaction.builder().id(400L).wallet(customerWallet)
                .type(WalletTransactionType.ALLOCATION).amountKopecks(-100_00L).balanceAfterKopecks(900_00L)
                .status(WalletTransactionStatus.DONE).build();
        when(getterWalletTransaction.getByIdForUpdate(400L)).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> service.reject(400L, TransferRejectRequestDTO.builder().reason("x").build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void payoutsListPutsOpenRequestsFirst() {
        WalletTransaction confirmed = WalletTransaction.builder().id(90L).wallet(creatorWallet)
                .type(WalletTransactionType.PAYOUT).amountKopecks(-5_000_00L).balanceAfterKopecks(0L)
                .status(WalletTransactionStatus.CONFIRMED).createdAt(Instant.now().plusSeconds(60)).build();
        when(getterWalletTransaction.getByType(WalletTransactionType.PAYOUT)).thenReturn(List.of(confirmed, pending));
        when(getterTransfer.getByTransactionIds(any())).thenReturn(List.of(payout));

        List<OperationRowDTO> rows = service.payouts();

        assertThat(rows).extracting(OperationRowDTO::id).containsExactly(100L, 90L);
        assertThat(rows.get(0).ownerName()).isEqualTo("Аня");
        assertThat(rows.get(0).status()).isEqualTo("PENDING");
        assertThat(rows.get(0).destination().label()).isEqualTo("TRON · " + TRON);
        assertThat(rows.get(1).destination().label()).isEqualTo("Кошелёк TRON");
    }
}
