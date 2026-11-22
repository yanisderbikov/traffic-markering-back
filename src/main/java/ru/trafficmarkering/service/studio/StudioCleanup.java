package ru.trafficmarkering.service.studio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.repository.StudioChatDraftRepository;
import ru.trafficmarkering.repository.StudioJobRepository;

import java.time.Duration;
import java.time.Instant;

/** Сроки хранения: готовый ролик 48 ч, исходники для повтора 24 ч, история 30 дней. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudioCleanup {

    private final StudioJobRepository jobs;
    private final StudioChatDraftRepository drafts;
    private final StudioService studio;
    private final StudioStorage storage;
    private final StudioProperties properties;
    private final TransactionTemplate tx;

    @Scheduled(fixedDelayString = "${studio.cleanup-ms:600000}", initialDelayString = "${studio.cleanup-initial-ms:120000}")
    public void run() {
        Instant now = Instant.now();
        try {
            for (StudioJob job : jobs.findByStatusAndFinishedAtBefore(StudioJobStatus.DONE,
                    now.minus(Duration.ofHours(properties.getResultTtlHours())))) {
                storage.delete(job.getResultKey());
                tx.executeWithoutResult(s -> jobs.findById(job.getId()).ifPresent(fresh -> {
                    fresh.setStatus(StudioJobStatus.EXPIRED);
                    fresh.setResultKey(null);
                    jobs.save(fresh);
                }));
            }
            for (StudioJob job : jobs.findWithExpiredInputs(now)) {
                tx.executeWithoutResult(s -> jobs.findById(job.getId()).ifPresent(fresh -> {
                    fresh.setInputsUntil(null);
                    jobs.save(fresh);
                }));
                studio.releaseInputsIfUnused(job);
            }
            Integer removed = tx.execute(s -> jobs.deleteHistoryBefore(now.minus(Duration.ofDays(properties.getHistoryTtlDays()))));
            tx.executeWithoutResult(s -> drafts.deleteOlderThan(now.minus(Duration.ofMinutes(30))));
            if (removed != null && removed > 0) {
                log.info("Видео-студия: удалено {} старых задач из истории", removed);
            }
        } catch (RuntimeException e) {
            log.error("Видео-студия: сбой очистки", e);
        }
    }
}
