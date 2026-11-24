package ru.trafficmarkering.service.views;

import java.util.UUID;

public interface ViewsSyncService {

    /**
     * Обходит отклики в работе и спрашивает у {@link ViewCountProvider} свежие просмотры.
     * Объявления, где просмотры изменились, пересчитываются по деньгам.
     *
     * @return сколько откликов реально обновили
     */
    int syncApproved();

    /**
     * Замер просмотров одного ролика прямо сейчас — тем же провайдером, что и фоновая синхронизация.
     * Замер пишется в историю отклика; по нему считается работа «ссылка и промокод».
     *
     * @throws org.springframework.web.server.ResponseStatusException 503/502, если провайдера нет
     *                                                                 или он не отдал просмотры
     */
    CapturedViews captureNow(UUID applicationId);
}
