package ru.trafficmarkering.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.service.application.PlacementService;

/** Конец срока размещения ссылки и закрепа: подтверждённые надбавки засчитываются и уходят в кошелёк. */
@Log4j2
@Component
@RequiredArgsConstructor
public class PlacementRetentionTask {

    private final PlacementService placementService;

    @Scheduled(
            fixedRateString = "${placements.retention.fixed-rate-ms:1800000}",
            initialDelayString = "${placements.retention.initial-delay-ms:120000}"
    )
    public void process() {
        try {
            placementService.finalizeDue();
        } catch (Exception e) {
            log.error("Шедулер срока размещения не сработал", e);
        }
    }
}
