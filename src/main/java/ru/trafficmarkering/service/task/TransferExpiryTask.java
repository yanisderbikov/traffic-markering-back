package ru.trafficmarkering.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.service.transfer.TransferService;

@Log4j2
@Component
@RequiredArgsConstructor
public class TransferExpiryTask {

    private final TransferService transferService;

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void process() {
        try {
            int expired = transferService.expireOverdue();
            if (expired > 0) {
                log.info("Просрочено заявок без оплаты или подтверждения: {}", expired);
            }
        } catch (Exception e) {
            log.error("Шедулер просрочки заявок не сработал", e);
        }
    }
}
