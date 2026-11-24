package ru.trafficmarkering.util;

import ru.trafficmarkering.model.application.RateParts;

/**
 * Расчёт работы «ссылка и промокод» по снимку просмотров при подаче.
 * Просмотры округляются вниз до сотен (как в TopGift), деньги — вниз до копейки, как у обычного
 * начисления в {@link PayoutCalculator}. Суммы надбавок считаются нарастающим итогом, поэтому база
 * и надбавки в сумме всегда дают ровно общую сумму, без потерянной или лишней копейки.
 */
public final class SnapshotPayout {

    /** Шаг округления оплачиваемых просмотров вниз */
    public static final long VIEWS_STEP = 100L;

    private SnapshotPayout() {
    }

    /**
     * Оплачиваемые просмотры работы: потолок (новичку антифрод режет просмотры одного ролика),
     * затем округление вниз до сотен.
     *
     * @param cap потолок просмотров; null — без потолка
     */
    public static long billableViews(long payableViews, Long cap) {
        long views = Math.max(0L, payableViews);
        if (cap != null) {
            views = Math.min(views, cap);
        }
        return views / VIEWS_STEP * VIEWS_STEP;
    }

    /**
     * Сумма за работу по ставке, обрезанная потолком на один ролик.
     *
     * @param maxPerVideoKopecks потолок вознаграждения за ролик; null — без потолка
     */
    public static long reward(long billableViews, long ratePerThousandKopecks, Long maxPerVideoKopecks) {
        long amount = PayoutCalculator.accrual(billableViews, ratePerThousandKopecks, Long.MAX_VALUE);
        return maxPerVideoKopecks != null ? Math.min(amount, maxPerVideoKopecks) : amount;
    }

    /** База и выбранные надбавки по отдельности; невыбранная надбавка — 0. */
    public static Breakdown breakdown(long billableViews, RateParts rates, boolean withLink, boolean withPromo) {
        long base = PayoutCalculator.accrual(billableViews, rates.base(), Long.MAX_VALUE);
        long withLinkTotal = PayoutCalculator.accrual(billableViews, rates.rate(withLink, false), Long.MAX_VALUE);
        long total = PayoutCalculator.accrual(billableViews, rates.rate(withLink, withPromo), Long.MAX_VALUE);
        return new Breakdown(base, withLinkTotal - base, total - withLinkTotal, total);
    }

    /** Суммы в копейках; gross = base + link + promo, до потолка на ролик и остатка бюджета. */
    public record Breakdown(long base, long link, long promo, long gross) {
    }
}
