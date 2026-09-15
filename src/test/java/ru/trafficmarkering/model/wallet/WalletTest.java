package ru.trafficmarkering.model.wallet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletTest {

    @Test
    void creditsAndDebitsBalance() {
        Wallet wallet = Wallet.builder().balanceKopecks(10_000L).build();

        wallet.adjust(5_000L);
        wallet.adjust(-3_000L);

        assertEquals(12_000L, wallet.getBalanceKopecks());
    }

    @Test
    void rejectsDebitBelowZeroWithoutChangingBalance() {
        Wallet wallet = Wallet.builder().balanceKopecks(1_000L).build();

        assertThrows(IllegalArgumentException.class, () -> wallet.adjust(-1_001L));
        assertEquals(1_000L, wallet.getBalanceKopecks());
    }

    @Test
    void rejectsZeroAdjustment() {
        Wallet wallet = Wallet.builder().balanceKopecks(1_000L).build();

        assertThrows(IllegalArgumentException.class, () -> wallet.adjust(0L));
        assertEquals(1_000L, wallet.getBalanceKopecks());
    }

    @Test
    void rejectsLongOverflowWithoutChangingBalance() {
        Wallet wallet = Wallet.builder().balanceKopecks(Long.MAX_VALUE).build();

        assertThrows(ArithmeticException.class, () -> wallet.adjust(1L));
        assertEquals(Long.MAX_VALUE, wallet.getBalanceKopecks());
    }
}
