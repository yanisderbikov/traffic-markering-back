package ru.trafficmarkering.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.notify.Broadcast;

import java.util.Collection;
import java.util.List;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    List<Broadcast> findAllByOrderByCreatedAtDesc(Pageable page);

    /** Получатель рассылки: учётка, её Telegram (если привязан) и роль */
    interface Recipient {
        Long getUserId();

        String getUsername();

        String getName();

        Long getTelegramId();
    }

    @Query(value = "SELECT u.id AS \"userId\", u.username AS \"username\", u.name AS \"name\", sp.telegram_id AS \"telegramId\" "
            + "FROM users u LEFT JOIN studio_profile sp ON sp.user_id = u.id "
            + "WHERE u.role IN (:roles) ORDER BY u.id", nativeQuery = true)
    List<Recipient> recipients(@Param("roles") Collection<String> roles);

    /** Сколько людей в каждой роли, сколько привязали Telegram (всего и за неделю) и у скольких есть почта */
    interface ReachRow {
        String getRole();

        long getTotal();

        long getTelegram();

        long getTelegramWeek();

        long getEmail();

        long getBoth();
    }

    @Query(value = "SELECT u.role AS \"role\", count(*) AS \"total\", count(sp.telegram_id) AS \"telegram\", "
            + "count(*) FILTER (WHERE sp.telegram_id IS NOT NULL AND sp.telegram_linked_at > now() - interval '7 days') "
            + "AS \"telegramWeek\", count(*) FILTER (WHERE u.username NOT LIKE 'tg:%') AS \"email\", "
            + "count(*) FILTER (WHERE sp.telegram_id IS NOT NULL AND u.username NOT LIKE 'tg:%') AS \"both\" "
            + "FROM users u LEFT JOIN studio_profile sp ON sp.user_id = u.id "
            + "WHERE u.role IN ('CREATOR', 'CUSTOMER') GROUP BY u.role", nativeQuery = true)
    List<ReachRow> reach();

    @Modifying
    @Transactional
    @Query(value = "UPDATE broadcast SET sent = sent + :sent, failed = failed + :failed WHERE id = :id",
            nativeQuery = true)
    void addResults(@Param("id") long id, @Param("sent") int sent, @Param("failed") int failed);

    @Modifying
    @Transactional
    @Query(value = "UPDATE broadcast SET status = 'DONE', finished_at = now() "
            + "WHERE id = :id AND status = 'SENDING' AND sent + failed >= total", nativeQuery = true)
    void finishIfComplete(@Param("id") long id);
}
