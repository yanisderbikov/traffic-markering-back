package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayoutCalculatorTest {

    @Test
    void accrual_wholeThousands() {
        // 10 000 просмотров по 350 ₽ за тысячу = 3 500 ₽
        assertEquals(3_500_00, PayoutCalculator.accrual(10_000, 350_00, Long.MAX_VALUE));
    }

    @Test
    void accrual_roundsDownToKopeck() {
        // 1 просмотр по 350 ₽ за тысячу = 35 копеек ровно
        assertEquals(35, PayoutCalculator.accrual(1, 350_00, Long.MAX_VALUE));
        // 1 просмотр по 1 копейке за тысячу — заработано меньше копейки, платить нечего
        assertEquals(0, PayoutCalculator.accrual(1, 1, Long.MAX_VALUE));
        // 999 просмотров по 100 ₽: 99,9 ₽ → вниз до 99,90 ₽
        assertEquals(99_90, PayoutCalculator.accrual(999, 100_00, Long.MAX_VALUE));
    }

    @Test
    void accrual_cappedByRemainingBudget() {
        // Заработано 3 500 ₽, но в бюджете осталось 1 000 ₽ — больше не начислим
        assertEquals(1_000_00, PayoutCalculator.accrual(10_000, 350_00, 1_000_00));
        // Бюджет исчерпан — начисления нет вовсе
        assertEquals(0, PayoutCalculator.accrual(10_000, 350_00, 0));
    }

    @Test
    void accrual_zeroViewsGivesZero() {
        assertEquals(0, PayoutCalculator.accrual(0, 350_00, 1_000_00));
    }

    @Test
    void accrual_hugeViewsDoNotOverflow() {
        // Миллиард просмотров по 500 ₽ — считаем через BigDecimal, long не переполняется
        assertEquals(500_000_000_00L, PayoutCalculator.accrual(1_000_000_000L, 500_00, Long.MAX_VALUE));
    }

    @Test
    void accrual_rejectsNegativeArguments() {
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.accrual(-1, 350_00, 1_000_00));
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.accrual(1_000, -1, 1_000_00));
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.accrual(1_000, 350_00, -1));
    }
}
