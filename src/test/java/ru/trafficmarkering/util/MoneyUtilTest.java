package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilTest {

    /** Разряды разделяет неразрывный пробел — иначе «35 000 ₽» рвётся по строке. */
    private static final String NBSP = "\u00A0";

    @Test
    void kopecksToString_alwaysTwoDecimals() {
        assertEquals("350.00", MoneyUtil.kopecksToString(350_00));
        assertEquals("0.01", MoneyUtil.kopecksToString(1));
        assertEquals("0.00", MoneyUtil.kopecksToString(0));
    }

    @Test
    void formatRubles_groupsThousandsAndKeepsKopecks() {
        assertEquals("3" + NBSP + "000" + NBSP + "₽", MoneyUtil.formatRubles(3_000_00));
        assertEquals("500" + NBSP + "₽", MoneyUtil.formatRubles(500_00));
        assertEquals("1" + NBSP + "234" + NBSP + "567" + NBSP + "₽", MoneyUtil.formatRubles(1_234_567_00));
        assertEquals("2" + NBSP + "999,50" + NBSP + "₽", MoneyUtil.formatRubles(2_999_50));
    }

    @Test
    void stringToKopecks_acceptsDotCommaAndSpaces() {
        assertEquals(350_00, MoneyUtil.stringToKopecks("350"));
        assertEquals(350_50, MoneyUtil.stringToKopecks("350.5"));
        // Из формы ставка приходит с запятой — так её набирают на русской раскладке
        assertEquals(350_50, MoneyUtil.stringToKopecks(" 350,50 "));
    }

    @Test
    void stringToKopecks_roundsToKopeck() {
        // Третий знак после запятой в рублях не бывает: округляем к ближайшей копейке
        assertEquals(1_00, MoneyUtil.stringToKopecks("0.999"));
        assertEquals(1, MoneyUtil.stringToKopecks("0.005"));
    }

    @Test
    void stringToKopecks_rejectsEmptyAndGarbage() {
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.stringToKopecks(null));
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.stringToKopecks("   "));
        assertThrows(NumberFormatException.class, () -> MoneyUtil.stringToKopecks("бесплатно"));
    }

    @Test
    void roundTrip_kopecksSurviveStringConversion() {
        long kopecks = 1_234_56;
        assertEquals(kopecks, MoneyUtil.stringToKopecks(MoneyUtil.kopecksToString(kopecks)));
    }
}
