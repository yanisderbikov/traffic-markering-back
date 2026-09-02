package ru.trafficmarkering.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Все деньги в проекте — копейки в {@code long}. Этот класс нужен только на границах:
 * показать сумму человеку или разобрать введённую им строку.
 */
public final class MoneyUtil {

    /** Неразрывный пробел: «35 000 ₽» не должно переноситься по строке. */
    private static final String GROUP_SEPARATOR = "\u00A0";

    private MoneyUtil() {
    }

    /** «350.00» — машинное представление для интеграций, всегда с двумя знаками. */
    public static String kopecksToString(long kopecks) {
        return BigDecimal.valueOf(kopecks)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** «3 000 ₽» или «2 999,50 ₽» — для сообщений пользователю. */
    public static String formatRubles(long kopecks) {
        long rub = kopecks / 100;
        long kop = Math.abs(kopecks % 100);
        String grouped = Long.toString(rub).replaceAll("\\B(?=(\\d{3})+(?!\\d))", GROUP_SEPARATOR);
        return kop == 0 ? grouped + GROUP_SEPARATOR + "₽" : grouped + String.format(",%02d", kop) + GROUP_SEPARATOR + "₽";
    }

    /** Разбор суммы в рублях из строки («350», «350.5», «350,50») в копейки. */
    public static long stringToKopecks(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Сумма не может быть пустой");
        }
        return new BigDecimal(value.trim().replace(',', '.'))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}
