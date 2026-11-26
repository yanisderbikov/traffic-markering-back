package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.trafficmarkering.model.moderation.ModerationAssignment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModerationAssignmentRepository extends JpaRepository<ModerationAssignment, Long> {

    List<ModerationAssignment> findByStatus(ModerationAssignment.Status status);

    Optional<ModerationAssignment> findFirstByModeratorIdAndStatus(Long moderatorId, ModerationAssignment.Status status);

    boolean existsByModeratorIdAndStatus(Long moderatorId, ModerationAssignment.Status status);

    /** Последний file_id ролика: повторная раздача уходит без скачивания */
    Optional<ModerationAssignment> findFirstByApplicationIdAndVideoFileIdNotNullOrderByIdDesc(UUID applicationId);

    /**
     * Следующий ролик для менеджера: на модерации и ни у кого сейчас не висит. Первыми — те, что этот
     * менеджер ещё не видел (пропущенное уходит другим), дальше — по времени подачи.
     * SKIP LOCKED — два диспетчера (например, во время деплоя) не возьмут один ролик.
     */
    @Query(value = """
            SELECT a.id FROM application a
            WHERE a.status = 'PENDING'
              AND NOT EXISTS (SELECT 1 FROM moderation_assignment m
                              WHERE m.application_id = a.id AND m.status = 'ACTIVE')
            ORDER BY (SELECT count(*) FROM moderation_assignment m
                      WHERE m.application_id = a.id AND m.moderator_id = :moderatorId) ASC,
                     COALESCE(a.submitted_at, a.created_at) ASC
            LIMIT 1
            FOR UPDATE OF a SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockNextApplication(@Param("moderatorId") Long moderatorId);

    @Query(value = "SELECT count(*) FROM application a WHERE a.status = 'PENDING' AND NOT EXISTS "
            + "(SELECT 1 FROM moderation_assignment m WHERE m.application_id = a.id AND m.status = 'ACTIVE')",
            nativeQuery = true)
    long countWaiting();

    @Query("SELECT m.moderator.id AS moderatorId, m.status AS status, m.decision AS decision, count(m) AS total "
            + "FROM ModerationAssignment m WHERE m.closedAt >= :since GROUP BY m.moderator.id, m.status, m.decision")
    List<ModeratorTally> tallySince(@Param("since") Instant since);

    long countByModeratorIdAndStatusInAndClosedAtGreaterThanEqual(Long moderatorId,
                                                                    Collection<ModerationAssignment.Status> statuses,
                                                                    Instant since);

    interface ModeratorTally {
        Long getModeratorId();

        ModerationAssignment.Status getStatus();

        ru.trafficmarkering.model.application.ApplicationStatus getDecision();

        long getTotal();
    }
}
