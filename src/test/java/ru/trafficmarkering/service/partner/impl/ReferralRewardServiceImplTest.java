package ru.trafficmarkering.service.partner.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.partner.Partner;
import ru.trafficmarkering.model.partner.ReferralReward;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterReferralReward;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverReferralReward;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.repository.SaverWalletTransaction;
import ru.trafficmarkering.service.wallet.impl.WalletLedgerTestSupport;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralRewardServiceImplTest {

    private final GetterWallet getterWallet = mock(GetterWallet.class);
    private final SaverWallet saverWallet = mock(SaverWallet.class);
    private final GetterWalletTransaction getterWalletTransaction = mock(GetterWalletTransaction.class);
    private final SaverWalletTransaction saverWalletTransaction = mock(SaverWalletTransaction.class);
    private final GetterReferralReward getterReferralReward = mock(GetterReferralReward.class);
    private final SaverReferralReward saverReferralReward = mock(SaverReferralReward.class);

    private final ReferralRewardServiceImpl service = new ReferralRewardServiceImpl(
            WalletLedgerTestSupport.ledger(getterWallet, saverWallet, getterWalletTransaction, saverWalletTransaction),
            getterReferralReward, saverReferralReward, new CommissionProperties());

    private final User partnerUser = User.builder().id(1L).username("partner@traffic.ru").name("Партнёр")
            .role(Role.CUSTOMER).build();
    private final Partner partner = Partner.builder().id(5L).user(partnerUser).code("K7Q2M9XA").build();
    private final Wallet partnerWallet = Wallet.builder().id(10L).user(partnerUser).balanceKopecks(100_00L).build();
    private final User friend = User.builder().id(2L).username("friend@traffic.ru").name("Друг")
            .role(Role.CUSTOMER).referredBy(partner).build();
    private final Wallet friendWallet = Wallet.builder().id(11L).user(friend).balanceKopecks(40_000_00L).build();
    private final WalletTransaction topUp = WalletTransaction.builder().id(300L).publicId("TU000300")
            .wallet(friendWallet).type(WalletTransactionType.TOP_UP).amountKopecks(40_000_00L)
            .balanceAfterKopecks(40_000_00L).status(WalletTransactionStatus.CONFIRMED).build();

    @BeforeEach
    void setUp() {
        when(saverWallet.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saverWalletTransaction.save(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction transaction = inv.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(400L);
            }
            return transaction;
        });
        when(saverReferralReward.save(any(ReferralReward.class))).thenAnswer(inv -> inv.getArgument(0));
        when(getterWallet.getByUserIdForUpdate(1L)).thenReturn(Optional.of(partnerWallet));
    }

    private Transfer transfer(long commissionKopecks) {
        return Transfer.builder().id(8L).transaction(topUp).commissionKopecks(commissionKopecks).build();
    }

    @Test
    void rewardsPartnerWithShareOfPlatformCommission() {
        service.reward(topUp, transfer(4_000_00L));

        assertThat(partnerWallet.balance()).isEqualTo(500_00L);
        ArgumentCaptor<ReferralReward> saved = ArgumentCaptor.forClass(ReferralReward.class);
        verify(saverReferralReward).save(saved.capture());
        ReferralReward reward = saved.getValue();
        assertThat(reward.getPartner()).isSameAs(partner);
        assertThat(reward.getReferral()).isSameAs(friend);
        assertThat(reward.getSourceTransaction()).isSameAs(topUp);
        assertThat(reward.getCommissionKopecks()).isEqualTo(4_000_00L);
        assertThat(reward.getRewardKopecks()).isEqualTo(400_00L);
        WalletTransaction credited = reward.getRewardTransaction();
        assertThat(credited.getWallet()).isSameAs(partnerWallet);
        assertThat(credited.getType()).isEqualTo(WalletTransactionType.REFERRAL_REWARD);
        assertThat(credited.getStatus()).isEqualTo(WalletTransactionStatus.DONE);
        assertThat(credited.getAmountKopecks()).isEqualTo(400_00L);
        assertThat(credited.getBalanceAfterKopecks()).isEqualTo(500_00L);
        assertThat(credited.getComment()).contains("Друг");
        assertThat(friendWallet.balance()).isEqualTo(40_000_00L);
    }

    @Test
    void customerWithoutPartnerBringsNothing() {
        friend.setReferredBy(null);

        service.reward(topUp, transfer(4_000_00L));

        assertThat(partnerWallet.balance()).isEqualTo(100_00L);
        verify(saverWalletTransaction, never()).save(any());
        verify(saverReferralReward, never()).save(any());
    }

    @Test
    void sameOperationIsRewardedOnce() {
        when(getterReferralReward.existsBySourceTransactionId(300L)).thenReturn(true);

        service.reward(topUp, transfer(4_000_00L));

        assertThat(partnerWallet.balance()).isEqualTo(100_00L);
        verify(saverReferralReward, never()).save(any());
    }

    @Test
    void operationWithoutCommissionBringsNothing() {
        service.reward(topUp, transfer(0L));
        service.reward(topUp, transfer(9L));

        assertThat(partnerWallet.balance()).isEqualTo(100_00L);
        verify(saverWalletTransaction, never()).save(any());
        verify(saverReferralReward, never()).save(any());
    }
}
