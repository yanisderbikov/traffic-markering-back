package ru.trafficmarkering.service.transfer.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.service.transfer.TransferService;

/**
 * Заявки, созданные до фиксации курса, курса не хранят. Исходный курс уже не узнать, поэтому открытым
 * заявкам при старте один раз записываем текущий — дальше он, как и у новых, не меняется.
 */
@Component
@RequiredArgsConstructor
@Log4j2
class TransferRateBootstrap implements ApplicationRunner {

    private final TransferService transferService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int fixed = transferService.fixMissingUsdtRates();
            if (fixed > 0) {
                log.info("Зафиксирован курс USDT у открытых заявок без курса: {}", fixed);
            }
        } catch (Exception e) {
            log.error("Не удалось зафиксировать курс у заявок без курса, повторим при следующем запуске", e);
        }
    }
}
