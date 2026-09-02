package ru.trafficmarkering.service.campaign;

import ru.trafficmarkering.model.campaign.Campaign;

public interface CampaignAccrualService {

    /**
     * Пересчитывает начисления по всему объявлению целиком и записывает spentKopecks.
     * Считаем не «дельту», а всё заново и в фиксированном порядке (отклики по created_at ASC):
     * так результат не зависит от того, сколько раз и в каком порядке приходили правки
     * просмотров, а сумма начислений всегда сходится с потраченным бюджетом.
     * Зовётся при любом изменении просмотров, статуса отклика, ставки или бюджета.
     */
    void recalculate(Campaign campaign);
}
