package ru.trafficmarkering.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudioJobRepository extends JpaRepository<StudioJob, UUID> {

    List<StudioJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<StudioJob> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<StudioJobStatus> statuses,
                                                                Pageable pageable);

    Optional<StudioJob> findByIdAndUserId(UUID id, Long userId);

    List<StudioJob> findByBatchIdOrderByCreatedAt(UUID batchId);

    @Query("SELECT count(j) FROM StudioJob j WHERE j.userId = :userId AND j.createdAt >= :since "
            + "AND j.status NOT IN (ru.trafficmarkering.model.studio.StudioJobStatus.FAILED, "
            + "ru.trafficmarkering.model.studio.StudioJobStatus.CANCELLED)")
    long countUsedSince(@Param("userId") Long userId, @Param("since") Instant since);

    long countByUserIdAndStatusIn(Long userId, Collection<StudioJobStatus> statuses);

    /** Место в очереди: сколько задач ждёт раньше этой */
    @Query("SELECT count(j) FROM StudioJob j WHERE j.status = ru.trafficmarkering.model.studio.StudioJobStatus.QUEUED "
            + "AND j.createdAt < :createdAt")
    long countQueuedBefore(@Param("createdAt") Instant createdAt);

    /**
     * Забирает следующую задачу из очереди. SKIP LOCKED — чтобы два экземпляра бэка
     * (например, во время деплоя) не взяли одну и ту же задачу.
     */
    @Query(value = "SELECT * FROM studio_job WHERE status = 'QUEUED' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<StudioJob> lockNextQueued();

    @Query("SELECT j.status FROM StudioJob j WHERE j.id = :id")
    Optional<StudioJobStatus> findStatus(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE StudioJob j SET j.progress = :progress WHERE j.id = :id AND j.status = "
            + "ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING")
    int updateProgress(@Param("id") UUID id, @Param("progress") int progress);

    @Modifying
    @Query("UPDATE StudioJob j SET j.heartbeatAt = :now WHERE j.id = :id AND j.status = "
            + "ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING")
    int heartbeat(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE StudioJob j SET j.progressMessageId = :messageId WHERE j.id = :id")
    int setProgressMessage(@Param("id") UUID id, @Param("messageId") Long messageId);

    /** Перезапуск: задача снова в очереди, следующий воркер соберёт её с начала */
    @Modifying
    @Query("UPDATE StudioJob j SET j.status = ru.trafficmarkering.model.studio.StudioJobStatus.QUEUED, "
            + "j.progress = 0, j.startedAt = NULL, j.heartbeatAt = NULL WHERE j.id = :id AND j.status = "
            + "ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING")
    int requeue(@Param("id") UUID id);

    /** RUNNING, но воркер давно не отзывался — экземпляр упал посреди сборки */
    @Query("SELECT j FROM StudioJob j WHERE j.status = ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING "
            + "AND COALESCE(j.heartbeatAt, j.startedAt) < :before")
    List<StudioJob> findStale(@Param("before") Instant before);

    List<StudioJob> findByStatusAndStartedAtBefore(StudioJobStatus status, Instant startedBefore);

    List<StudioJob> findByStatusAndFinishedAtBefore(StudioJobStatus status, Instant finishedBefore);

    @Query("SELECT j FROM StudioJob j WHERE j.inputsUntil IS NOT NULL AND j.inputsUntil < :now "
            + "AND j.status NOT IN (ru.trafficmarkering.model.studio.StudioJobStatus.QUEUED, "
            + "ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING)")
    List<StudioJob> findWithExpiredInputs(@Param("now") Instant now);

    @Query("SELECT count(j) FROM StudioJob j WHERE j.sourceKey = :key AND j.id <> :id AND j.status IN "
            + "(ru.trafficmarkering.model.studio.StudioJobStatus.QUEUED, ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING)")
    long countOtherActiveWithSource(@Param("key") String key, @Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM StudioJob j WHERE j.createdAt < :before AND j.status NOT IN "
            + "(ru.trafficmarkering.model.studio.StudioJobStatus.QUEUED, ru.trafficmarkering.model.studio.StudioJobStatus.RUNNING)")
    int deleteHistoryBefore(@Param("before") Instant before);
}
