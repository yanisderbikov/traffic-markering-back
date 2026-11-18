package ru.trafficmarkering.service.fraud;

import ru.trafficmarkering.model.application.Application;

public interface FraudCheckService {

    /**
     * Прогоняет отклик через скоринг по его истории замеров и обновляет статус, баллы и флаги.
     * Ручное решение админа автоматика не перебивает: тогда меняются только баллы и флаги.
     *
     * @return true, если после проверки изменилось, идут ли по отклику начисления —
     *         объявление в таком случае нужно пересчитать
     */
    boolean check(Application application);
}
