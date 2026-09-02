package ru.trafficmarkering.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.service.views.ViewsSyncService;

@Log4j2
@Component
@RequiredArgsConstructor
public class ViewsSyncTask {

    private final ViewsSyncService viewsSyncService;

    /** Рубильник шедулера: на локальной разработке и в тестовых стендах его обычно гасят */
    @Value("${views.sync.enabled}")
    private boolean enabled;

    /**
     * Периодический опрос просмотров по откликам в работе.
     * Интервал и задержка первого запуска — из настроек: пока провайдер заглушка,
     * ходить часто незачем, а с реальным счётчиком темп подберётся по лимитам площадок.
     */
    @Scheduled(
            fixedRateString = "${views.sync.fixed-rate-ms}",
            initialDelayString = "${views.sync.initial-delay-ms}"
    )
    public void process() {
        if (!enabled) {
            return;
        }
        try {
            viewsSyncService.syncApproved();
        } catch (Exception e) {
            log.error("Шедулер синхронизации просмотров не сработал", e);
        }
    }
}
