package ru.trafficmarkering.service.views;

import java.util.UUID;

public interface ViewsSyncService {

    /**
     * Замер просмотров одного ролика прямо сейчас. Делается один раз — при подаче работы на проверку
     * (для «ссылки и промокода» — при расчёте перед подачей); фонового обновления просмотров нет.
     * Замер пишется в историю отклика, по нему считаются деньги.
     *
     * @throws org.springframework.web.server.ResponseStatusException 503/502, если провайдера нет
     *                                                                 или он не отдал просмотры
     */
    CapturedViews captureNow(UUID applicationId);
}
