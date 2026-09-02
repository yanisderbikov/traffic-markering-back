package ru.trafficmarkering.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Начисление криатору за просмотры: views / 1000 * ставка, округление ВНИЗ до копейки —
 * платформа не выплачивает больше, чем реально набрано. Результат не превышает остаток
 * бюджета объявления: закончился бюджет — начисление обрезается.
 */
public final class PayoutCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private PayoutCalculator() {
    }

    public static long accrual(long views, long ratePerThousandKopecks, long budgetRemainingKopecks) {
        if (views < 0) {
            throw new IllegalArgumentException("Просмотры не могут быть отрицательными: " + views);
        }
        if (ratePerThousandKopecks < 0) {
            throw new IllegalArgumentException("Ставка не может быть отрицательной: " + ratePerThousandKopecks);
        }
        if (budgetRemainingKopecks < 0) {
            throw new IllegalArgumentException("Остаток бюджета не может быть отрицательным: " + budgetRemainingKopecks);
        }
        long earned = BigDecimal.valueOf(views)
                .multiply(BigDecimal.valueOf(ratePerThousandKopecks))
                .divide(THOUSAND, 0, RoundingMode.DOWN)
                .longValue();
        return Math.min(earned, budgetRemainingKopecks);
    }
}
