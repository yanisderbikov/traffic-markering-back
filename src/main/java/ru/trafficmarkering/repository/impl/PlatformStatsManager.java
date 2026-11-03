package ru.trafficmarkering.repository.impl;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.repository.GetterPlatformStats;
import ru.trafficmarkering.repository.PlatformSeriesPoint;
import ru.trafficmarkering.repository.PlatformStats;
import ru.trafficmarkering.repository.SeriesStep;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Определения метрик:
 * <ul>
 *     <li>креаторы и заказчики — учётки с подтверждённой почтой, «новые» — подтверждённые за период;</li>
 *     <li>опубликованные видео — отклики с роликом, прошедшие модерацию (APPROVED и COMPLETED),
 *     дата — публикации на площадке, а если площадка её не отдала — создания отклика;</li>
 *     <li>рекламный оборот — начисления креаторам за просмотры: за всё время это сумма
 *     campaign.spent_kopecks, за период — проводки EARNING;</li>
 *     <li>комиссия — с подтверждённых пополнений, выводов и выплат, партнёрские вознаграждения
 *     платятся из неё же.</li>
 * </ul>
 */
@Component
@AllArgsConstructor
@Log4j2
class PlatformStatsManager implements GetterPlatformStats {

    private static final String PUBLISHED = "a.video_url is not null and a.status in ('APPROVED', 'COMPLETED')";

    private static final String STATS_SQL = "select "
            + "(select count(*) from users where role = 'CREATOR' and verified_at is not null), "
            + "(select count(*) from users where role = 'CREATOR' and verified_at >= :since), "
            + "(select count(*) from users where role = 'CUSTOMER' and verified_at is not null), "
            + "(select count(*) from users where role = 'CUSTOMER' and verified_at >= :since), "
            + "(select count(*) from application a where " + PUBLISHED + "), "
            + "(select count(*) from application a where " + PUBLISHED
            + " and coalesce(a.video_published_at, a.created_at) >= :since), "
            + "(select coalesce(sum(a.views), 0) from application a where " + PUBLISHED + "), "
            + "(select count(*) from campaign where status = 'ACTIVE'), "
            + "(select coalesce(sum(spent_kopecks), 0) from campaign), "
            + "(select coalesce(sum(amount_kopecks), 0) from wallet_transaction "
            + " where type = 'EARNING' and status not in ('REJECTED', 'CANCELLED') and created_at >= :since), "
            + "(select coalesce(sum(tx.amount_kopecks), 0) from transfer t join wallet_transaction tx on tx.id = t.transaction_id "
            + " where tx.type = 'TOP_UP' and tx.status = 'CONFIRMED'), "
            + "(select coalesce(sum(tx.amount_kopecks), 0) from transfer t join wallet_transaction tx on tx.id = t.transaction_id "
            + " where tx.type = 'TOP_UP' and tx.status = 'CONFIRMED' and t.confirmed_at >= :since), "
            + "(select coalesce(sum(t.commission_kopecks), 0) from transfer t join wallet_transaction tx on tx.id = t.transaction_id "
            + " where tx.status = 'CONFIRMED'), "
            + "(select coalesce(sum(t.commission_kopecks), 0) from transfer t join wallet_transaction tx on tx.id = t.transaction_id "
            + " where tx.status = 'CONFIRMED' and t.confirmed_at >= :since), "
            + "(select coalesce(sum(reward_kopecks), 0) from referral_reward), "
            + "(select coalesce(sum(reward_kopecks), 0) from referral_reward where created_at >= :since)";

    private static final String IN_BUCKET = " >= b.start_at and %s < b.end_at";

    /**
     * Шаги режем по локальному времени зрителя (иначе «сутки» съедут на его часовой пояс),
     * а границы возвращаем как timestamptz. Просмотры на границе — последний замер каждого
     * опубликованного ролика до неё: так прирост считается по истории, а не по текущему счётчику.
     */
    private static final String SERIES_SQL = "with b as ("
            + " select (s at time zone :tz) as start_at, ((s + cast(:step as interval)) at time zone :tz) as end_at"
            + " from generate_series(date_trunc(:unit, cast(:from as timestamptz) at time zone :tz),"
            + " cast(:to as timestamptz) at time zone :tz, cast(:step as interval)) s) "
            + "select b.start_at, b.end_at, "
            + "(select count(*) from users u where u.role = 'CREATOR' and u.verified_at" + IN_BUCKET.formatted("u.verified_at") + "), "
            + "(select count(*) from users u where u.role = 'CUSTOMER' and u.verified_at" + IN_BUCKET.formatted("u.verified_at") + "), "
            + "(select count(*) from application a where " + PUBLISHED
            + " and coalesce(a.video_published_at, a.created_at)" + IN_BUCKET.formatted("coalesce(a.video_published_at, a.created_at)") + "), "
            + viewsAt("b.start_at") + ", "
            + viewsAt("b.end_at") + ", "
            + "(select coalesce(sum(tx.amount_kopecks), 0) from wallet_transaction tx where tx.type = 'EARNING'"
            + " and tx.status not in ('REJECTED', 'CANCELLED') and tx.created_at" + IN_BUCKET.formatted("tx.created_at") + "), "
            + "(select coalesce(sum(tx.amount_kopecks), 0) from transfer t join wallet_transaction tx on tx.id = t.transaction_id"
            + " where tx.type = 'TOP_UP' and tx.status = 'CONFIRMED' and t.confirmed_at" + IN_BUCKET.formatted("t.confirmed_at") + "), "
            + "(select coalesce(sum(t.commission_kopecks), 0) from transfer t join wallet_transaction tx on tx.id = t.transaction_id"
            + " where tx.status = 'CONFIRMED' and t.confirmed_at" + IN_BUCKET.formatted("t.confirmed_at") + "), "
            + "(select coalesce(sum(r.reward_kopecks), 0) from referral_reward r where r.created_at" + IN_BUCKET.formatted("r.created_at") + ") "
            + "from b order by b.start_at";

    private final EntityManager entityManager;

    private static String viewsAt(String boundary) {
        return "(select coalesce(sum(v.views), 0) from application a cross join lateral"
                + " (select s.views from application_view_snapshot s where s.application_id = a.id"
                + " and s.captured_at < " + boundary + " order by s.captured_at desc limit 1) v where " + PUBLISHED + ")";
    }

    @Override
    public PlatformStats getStats(Instant since) {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(STATS_SQL)
                    .setParameter("since", since)
                    .getSingleResult();
            return new PlatformStats(
                    asLong(row[0]), asLong(row[1]), asLong(row[2]), asLong(row[3]),
                    asLong(row[4]), asLong(row[5]), asLong(row[6]), asLong(row[7]),
                    asLong(row[8]), asLong(row[9]), asLong(row[10]), asLong(row[11]),
                    asLong(row[12]), asLong(row[13]), asLong(row[14]), asLong(row[15]));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PlatformSeriesPoint> getSeries(Instant from, Instant to, SeriesStep step, ZoneId zone) {
        try {
            List<Object[]> rows = entityManager.createNativeQuery(SERIES_SQL)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .setParameter("tz", zone.getId())
                    .setParameter("unit", step.unit())
                    .setParameter("step", step.interval())
                    .getResultList();
            return rows.stream()
                    .map(row -> new PlatformSeriesPoint(
                            asInstant(row[0]), asInstant(row[1]),
                            asLong(row[2]), asLong(row[3]), asLong(row[4]), asLong(row[5]), asLong(row[6]),
                            asLong(row[7]), asLong(row[8]), asLong(row[9]), asLong(row[10])))
                    .toList();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Instant> getFirstActivityAt() {
        try {
            Object value = entityManager.createNativeQuery("select min(created_at) from users").getSingleResult();
            return Optional.ofNullable(value).map(PlatformStatsManager::asInstant);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    private static Instant asInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Неожиданный тип времени: " + value);
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
