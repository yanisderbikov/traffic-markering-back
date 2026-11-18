package ru.trafficmarkering.service.transfer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.dto.rate.UsdtRateDTO;
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
import ru.trafficmarkering.service.partner.ReferralRewardService;
import ru.trafficmarkering.service.rate.UsdtRateService;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.wallet.WalletService;
import ru.trafficmarkering.service.wallet.impl.WalletLedgerTestSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private final UsdtRateService usdtRateService = mock(UsdtRateService.class);
    private final ReferralRewardService referralRewardService = mock(ReferralRewardService.class);

    private final TransferServiceImpl service = new TransferServiceImpl(
            WalletLedgerTestSupport.ledger(getterWallet, saverWallet, getterWalletTransaction, saverWalletTransaction),
            WalletLedgerTestSupport.reader(getterTransfer, fileStorage),
            getterWalletTransaction, getterTransfer, saverTransfer, walletService, currentUserService, usdtRateService,
            new CommissionProperties(), referralRewardService);

    private final User creator = User.builder().id(1L).username("anna@traffic.ru").name("Аня").role(Role.CREATOR).build();
    private final User customer = User.builder().id(3L).username("customer@traffic.ru").name("Заказчик").role(Role.CUSTOMER).build();
    private final User finance = User.builder().id(2L).username("money@traffic.ru").name("Маша").role(Role.FINANCE_MANAGER).build();
    private final Wallet creatorWallet = Wallet.builder().id(10L).user(creator).balanceKopecks(2_000_00L).build();
    private final Wallet customerWallet = Wallet.builder().id(11L).user(customer).balanceKopecks(1_000_00L).build();
    private final WalletTransaction pending = WalletTransaction.builder().id(100L).publicId("PO000100").wallet(creatorWallet)
            .type(WalletTransactionType.PAYOUT).amountKopecks(-5_000_00L).balanceAfterKopecks(2_000_00L)
            .status(WalletTransactionStatus.PENDING).createdAt(Instant.now()).build();
    private final Transfer payout = Transfer.builder().id(7L).transaction(pending).tronAddress(TRON).build();

    @BeforeEach
    void setUp() {
        when(currentUserService.require(Role.FINANCE_MANAGER)).thenReturn(finance);
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        when(walletService.topUpTronAddress()).thenReturn(PLATFORM_TRON);
        when(usdtRateService.current()).thenReturn(rate("86.76"));
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
        when(getterWalletTransaction.getByPublicIdForUpdate("PO000100")).thenReturn(Optional.of(pending));
        when(getterTransfer.getByTransactionId(100L)).thenReturn(Optional.of(payout));
        when(fileStorage.presignedUrl(any())).thenAnswer(inv -> "https://s3/" + inv.getArgument(0));
    }

    private WalletOperationRequestDTO.WalletOperationRequestDTOBuilder operation(long amountKopecks) {
        return WalletOperationRequestDTO.builder().amountKopecks(amountKopecks).txId(" tx-1 ").proofKeys(PROOFS);
    }

    private UsdtRateDTO rate(String askPrice) {
        return new UsdtRateDTO(new BigDecimal(askPrice), new BigDecimal(askPrice), Instant.now().toString(), "Rapira");
    }

    private WalletTransaction topUp(WalletTransactionStatus status) {
        WalletTransaction topUp = WalletTransaction.builder().id(300L).publicId("TU000300").wallet(customerWallet)
                .type(WalletTransactionType.TOP_UP).amountKopecks(800_00L).balanceAfterKopecks(1_000_00L)
                .status(status).actor(customer).createdAt(Instant.now()).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("TU000300")).thenReturn(Optional.of(topUp));
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
        assertThat(detail.transfer().usdtRate()).isEqualByComparingTo("86.76");
        assertThat(detail.transfer().commissionKopecks()).isEqualTo(250_00L);
        assertThat(detail.transfer().transferKopecks()).isEqualTo(2_750_00L);
        assertThat(Instant.parse(detail.transfer().expiresAt()))
                .isBetween(Instant.now().plus(TransferServiceImpl.TOP_UP_TTL).minusSeconds(5),
                        Instant.now().plus(TransferServiceImpl.TOP_UP_TTL));
    }

    @Test
    void markTopUpPaidRefusesExpiredRequest() {
        Transfer transfer = topUpTransfer(topUp(WalletTransactionStatus.PENDING));
        transfer.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.markTopUpPaid("TU000300", TopUpPaidRequestDTO.builder().proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(transfer.getSentAt()).isNull();
    }

    @Test
    void expireOverdueTopUpsClosesOnlyUnpaidRequests() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.PENDING);
        Transfer transfer = topUpTransfer(topUp);
        transfer.setExpiresAt(Instant.now().minusSeconds(1));
        when(getterTransfer.getOverdue(eq(WalletTransactionType.TOP_UP), eq(WalletTransactionStatus.PENDING),
                any(Instant.class))).thenReturn(List.of(transfer));

        assertThat(service.expireOverdue()).isEqualTo(1);
        assertThat(topUp.getStatus()).isEqualTo(WalletTransactionStatus.EXPIRED);
        assertThat(transfer.getClosedAt()).isNotNull();
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);

        topUp.setStatus(WalletTransactionStatus.SENT);
        assertThat(service.expireOverdue()).isZero();
        assertThat(topUp.getStatus()).isEqualTo(WalletTransactionStatus.SENT);
    }

    @Test
    void confirmTopUpStillCreditsExpiredRequest() {
        topUpTransfer(topUp(WalletTransactionStatus.EXPIRED));

        OperationDetailDTO detail = service.confirmTopUp("TU000300");

        assertThat(detail.transaction().status()).isEqualTo("CONFIRMED");
        assertThat(customerWallet.balance()).isEqualTo(1_800_00L);
    }

    @Test
    void topUpKeepsRateFixedAtCreationWhenMarketMoves() {
        service.requestTopUp(TopUpCreateRequestDTO.builder().amountKopecks(2_500_00L).build());
        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(saverTransfer).save(saved.capture());
        Transfer transfer = saved.getValue();
        WalletTransaction transaction = transfer.getTransaction();
        when(getterWalletTransaction.getByPublicIdForUpdate(transaction.getPublicId())).thenReturn(Optional.of(transaction));
        when(getterTransfer.getByTransactionId(transaction.getId())).thenReturn(Optional.of(transfer));
        when(usdtRateService.current()).thenReturn(rate("95.10"));

        OperationDetailDTO paid = service.markTopUpPaid(transaction.getPublicId(),
                TopUpPaidRequestDTO.builder().proofKeys(PROOFS).build());

        assertThat(paid.transfer().usdtRate()).isEqualByComparingTo("86.76");
    }

    @Test
    void fixMissingUsdtRatesFixesCurrentRateOnUnsettledTransfers() {
        when(getterTransfer.getWithoutUsdtRate(any())).thenReturn(List.of(payout));

        assertThat(service.fixMissingUsdtRates()).isEqualTo(1);
        assertThat(payout.getUsdtRate()).isEqualByComparingTo("86.76");
        verify(saverTransfer).save(payout);
    }

    @Test
    void fixMissingUsdtRatesDoesNotAskRateWhenNothingIsMissing() {
        when(getterTransfer.getWithoutUsdtRate(any())).thenReturn(List.of());

        assertThat(service.fixMissingUsdtRates()).isZero();
        verify(usdtRateService, never()).current();
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

        OperationDetailDTO detail = service.markTopUpPaid("TU000300", TopUpPaidRequestDTO.builder()
                .txId("  ").proofKeys(List.of("transfer-proofs/1.png", " transfer-proofs/2.pdf ")).build());

        assertThat(detail.transaction().status()).isEqualTo("SENT");
        assertThat(detail.transfer().txId()).isNull();
        assertThat(detail.transfer().proofs()).extracting("key")
                .containsExactly("transfer-proofs/1.png", "transfer-proofs/2.pdf");
        assertThat(detail.transfer().processedByName()).isNull();
        assertThat(transfer.getSentAt()).isNotNull();
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);

        assertThatThrownBy(() -> service.markTopUpPaid("TU000300", TopUpPaidRequestDTO.builder().proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markTopUpPaidRejectsForeignProofKeys() {
        topUpTransfer(topUp(WalletTransactionStatus.PENDING));

        assertThatThrownBy(() -> service.markTopUpPaid("TU000300",
                TopUpPaidRequestDTO.builder().proofKeys(List.of("campaign-photos/x.png")).build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ключ файла");
    }

    @Test
    void markTopUpPaidHidesForeignRequest() {
        WalletTransaction foreign = topUp(WalletTransactionStatus.PENDING);
        foreign.setWallet(creatorWallet);

        assertThatThrownBy(() -> service.markTopUpPaid("TU000300", TopUpPaidRequestDTO.builder().proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelTopUpOnlyBeforePayment() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.PENDING);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.cancelTopUp("TU000300");

        assertThat(detail.transaction().status()).isEqualTo("CANCELLED");
        assertThat(transfer.getClosedAt()).isNotNull();

        topUp.setStatus(WalletTransactionStatus.SENT);
        assertThatThrownBy(() -> service.cancelTopUp("TU000300"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmTopUpCreditsBalanceOnce() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.SENT);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.confirmTopUp("TU000300");

        assertThat(customerWallet.balance()).isEqualTo(1_800_00L);
        assertThat(detail.transaction().status()).isEqualTo("CONFIRMED");
        assertThat(detail.transaction().balanceAfterKopecks()).isEqualTo(1_800_00L);
        assertThat(detail.transfer().processedByName()).isEqualTo("Маша");
        assertThat(transfer.getConfirmedAt()).isNotNull();
        verify(referralRewardService).reward(topUp, transfer);

        assertThatThrownBy(() -> service.confirmTopUp("TU000300"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(customerWallet.balance()).isEqualTo(1_800_00L);
    }

    @Test
    void confirmTopUpCreditsAmountWithoutCommission() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.SENT);
        Transfer transfer = topUpTransfer(topUp);
        transfer.setCommissionKopecks(80_00L);

        OperationDetailDTO detail = service.confirmTopUp("TU000300");

        assertThat(customerWallet.balance()).isEqualTo(1_800_00L);
        assertThat(detail.transfer().commissionKopecks()).isEqualTo(80_00L);
        assertThat(detail.transfer().transferKopecks()).isEqualTo(880_00L);
    }

    @Test
    void rejectedTopUpBringsNoPartnerReward() {
        topUpTransfer(topUp(WalletTransactionStatus.SENT));

        service.reject("TU000300", TransferRejectRequestDTO.builder().reason("Не пришло").build());

        verify(referralRewardService, never()).reward(any(), any());
    }

    @Test
    void confirmTopUpRefusesPayouts() {
        assertThatThrownBy(() -> service.confirmTopUp("PO000100"))
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
        assertThat(detail.transfer().usdtRate()).isEqualByComparingTo("86.76");
        assertThat(detail.transfer().expiresAt()).isNotNull();
        assertThat(detail.transfer().commissionKopecks()).isEqualTo(40_00L);
        assertThat(detail.transfer().transferKopecks()).isEqualTo(360_00L);
    }

    @Test
    void expireOverdueClosesUnconfirmedWithdrawalAndKeepsMoneyOut() {
        WalletTransaction withdrawal = WalletTransaction.builder().id(500L).publicId("WD000500").wallet(customerWallet)
                .type(WalletTransactionType.WITHDRAWAL).amountKopecks(-300_00L).balanceAfterKopecks(1_000_00L)
                .status(WalletTransactionStatus.SENT).createdAt(Instant.now()).build();
        Transfer transfer = Transfer.builder().id(9L).transaction(withdrawal).tronAddress(TRON)
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("WD000500")).thenReturn(Optional.of(withdrawal));
        when(getterTransfer.getByTransactionId(500L)).thenReturn(Optional.of(transfer));
        when(getterTransfer.getOverdue(eq(WalletTransactionType.WITHDRAWAL), eq(WalletTransactionStatus.SENT),
                any(Instant.class))).thenReturn(List.of(transfer));

        assertThat(service.expireOverdue()).isEqualTo(1);
        assertThat(withdrawal.getStatus()).isEqualTo(WalletTransactionStatus.EXPIRED);
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);

        service.reject("WD000500", TransferRejectRequestDTO.builder().reason("Перевод не дошёл").build());

        assertThat(withdrawal.getStatus()).isEqualTo(WalletTransactionStatus.REJECTED);
        assertThat(customerWallet.balance()).isEqualTo(1_300_00L);
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
        OperationDetailDTO detail = service.markPayoutSent("PO000100", TransferSentRequestDTO.builder()
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
        assertThatThrownBy(() -> service.markPayoutSent("PO000100",
                TransferSentRequestDTO.builder().txId("abc").proofKeys(List.of("  ")).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pending.getStatus()).isEqualTo(WalletTransactionStatus.PENDING);
    }

    @Test
    void markPayoutSentOnlyFromPending() {
        pending.setStatus(WalletTransactionStatus.SENT);

        assertThatThrownBy(() -> service.markPayoutSent("PO000100",
                TransferSentRequestDTO.builder().txId("abc").proofKeys(PROOFS).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectPayoutReturnsMoneyAndKeepsReason() {
        OperationDetailDTO detail = service.reject("PO000100",
                TransferRejectRequestDTO.builder().reason(" Адрес не TRC-20 ").build());

        assertThat(detail.transaction().status()).isEqualTo("REJECTED");
        assertThat(detail.transfer().rejectReason()).isEqualTo("Адрес не TRC-20");
        assertThat(creatorWallet.balance()).isEqualTo(7_000_00L);
        assertThat(payout.getClosedAt()).isNotNull();

        assertThatThrownBy(() -> service.reject("PO000100", TransferRejectRequestDTO.builder().reason("ещё раз").build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(creatorWallet.balance()).isEqualTo(7_000_00L);
    }

    @Test
    void rejectTopUpClosesRequestWithoutTouchingBalance() {
        WalletTransaction topUp = topUp(WalletTransactionStatus.SENT);
        Transfer transfer = topUpTransfer(topUp);

        OperationDetailDTO detail = service.reject("TU000300", TransferRejectRequestDTO.builder().reason("Перевод не пришёл").build());

        assertThat(detail.transaction().status()).isEqualTo("REJECTED");
        assertThat(detail.transfer().rejectReason()).isEqualTo("Перевод не пришёл");
        assertThat(customerWallet.balance()).isEqualTo(1_000_00L);
        assertThat(transfer.getProcessedBy()).isSameAs(finance);
    }

    @Test
    void rejectRefusesInternalOperations() {
        WalletTransaction allocation = WalletTransaction.builder().id(400L).publicId("AL000400").wallet(customerWallet)
                .type(WalletTransactionType.ALLOCATION).amountKopecks(-100_00L).balanceAfterKopecks(900_00L)
                .status(WalletTransactionStatus.DONE).build();
        when(getterWalletTransaction.getByPublicIdForUpdate("AL000400")).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> service.reject("AL000400", TransferRejectRequestDTO.builder().reason("x").build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void payoutsListPutsOpenRequestsFirst() {
        WalletTransaction confirmed = WalletTransaction.builder().id(90L).publicId("PO000090").wallet(creatorWallet)
                .type(WalletTransactionType.PAYOUT).amountKopecks(-5_000_00L).balanceAfterKopecks(0L)
                .status(WalletTransactionStatus.CONFIRMED).createdAt(Instant.now().plusSeconds(60)).build();
        when(getterWalletTransaction.getByType(WalletTransactionType.PAYOUT)).thenReturn(List.of(confirmed, pending));
        when(getterTransfer.getByTransactionIds(any())).thenReturn(List.of(payout));

        List<OperationRowDTO> rows = service.payouts();

        assertThat(rows).extracting(OperationRowDTO::publicId).containsExactly("PO000100", "PO000090");
        assertThat(rows.get(0).ownerName()).isEqualTo("Аня");
        assertThat(rows.get(0).status()).isEqualTo("PENDING");
        assertThat(rows.get(0).destination().label()).isEqualTo("TRON · " + TRON);
        assertThat(rows.get(1).destination().label()).isEqualTo("Кошелёк TRON");
    }
}
