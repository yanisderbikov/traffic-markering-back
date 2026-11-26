package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.model.application.RateParts;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Приёмочные примеры из ТЗ «ссылка и промокод» в копейках: полная ставка 100 = база 50 + ссылка 30 + промокод 20 (как $1 = $0,50 + $0,30 + $0,20). */
class SnapshotPayoutTest {

    private static final RateParts RATES = new RateParts(50, 30, 20);
    private final CommissionProperties commission = new CommissionProperties();

    @Test
    void fourVariants_tenThousandViews() {
        assertEquals(1_000, gross(10_000, true, true));
        assertEquals(800, gross(10_000, true, false));
        assertEquals(700, gross(10_000, false, true));
        assertEquals(500, gross(10_000, false, false));
        // Комиссия считается один раз от итоговой суммы: 1 000 → 100 → 900 после комиссии
        assertEquals(100, commission.commissionOf(gross(10_000, true, true)));
        assertEquals(720, gross(10_000, true, false) - commission.commissionOf(gross(10_000, true, false)));
        assertEquals(630, gross(10_000, false, true) - commission.commissionOf(gross(10_000, false, true)));
        assertEquals(450, gross(10_000, false, false) - commission.commissionOf(gross(10_000, false, false)));
    }

    @Test
    void billableViews_roundDownToHundreds() {
        long billable = SnapshotPayout.billableViews(3_887, null);
        assertEquals(3_800, billable);
        long gross = SnapshotPayout.reward(billable, RATES.full(), null);
        assertEquals(380, gross);
        assertEquals(38, commission.commissionOf(gross));
        assertEquals(342, gross - commission.commissionOf(gross));
    }

    @Test
    void billableViews_capBeforeRounding_zeroIsValid() {
        assertEquals(50_000, SnapshotPayout.billableViews(73_456, 50_000L));
        assertEquals(0, SnapshotPayout.billableViews(0, null));
        assertEquals(0, SnapshotPayout.billableViews(99, null));
    }

    @Test
    void breakdown_componentsSumToGrossWithoutLostKopeck() {
        // 1 300 просмотров по 333/111/77: по отдельности округлённые части давали бы на копейку меньше
        RateParts odd = new RateParts(333, 111, 77);
        SnapshotPayout.Breakdown breakdown = SnapshotPayout.breakdown(1_300, odd, true, true);
        assertEquals(breakdown.gross(), breakdown.base() + breakdown.link() + breakdown.promo());
        assertEquals(SnapshotPayout.reward(1_300, odd.full(), null), breakdown.gross());
        SnapshotPayout.Breakdown baseOnly = SnapshotPayout.breakdown(1_300, odd, false, false);
        assertEquals(0, baseOnly.link());
        assertEquals(0, baseOnly.promo());
    }

    @Test
    void reward_cappedPerVideo() {
        assertEquals(700, SnapshotPayout.reward(10_000, RATES.full(), 700L));
        assertEquals(1_000, SnapshotPayout.reward(10_000, RATES.full(), null));
    }

    private static long gross(long views, boolean withLink, boolean withPromo) {
        return SnapshotPayout.reward(SnapshotPayout.billableViews(views, null), RATES.rate(withLink, withPromo), null);
    }
}
