package ru.trafficmarkering.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.service.earnings.EarningsService;

@Log4j2
@Component
@RequiredArgsConstructor
public class EarningsCreditTask {

    private final EarningsService earningsService;

    @Value("${earnings.enabled}")
    private boolean enabled;

    @Scheduled(cron = "${earnings.cron}", zone = "${earnings.zone}")
    public void process() {
        if (!enabled) {
            return;
        }
        try {
            earningsService.creditAccrued();
        } catch (Exception e) {
            log.error("Ночное начисление в кошельки криаторов не сработало", e);
        }
    }
}
