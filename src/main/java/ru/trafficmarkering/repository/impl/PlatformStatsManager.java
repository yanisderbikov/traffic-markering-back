package ru.trafficmarkering.repository.impl;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.repository.GetterPlatformStats;
import ru.trafficmarkering.repository.PlatformStats;

import java.time.Instant;

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

    private final EntityManager entityManager;

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

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
