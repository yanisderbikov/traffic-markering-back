package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.service.views.ViewsSyncService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Догоняет просмотры по откликам в работе. Пока провайдер — заглушка, проход
 * получается холостым, но вся обвязка (что обновляем, когда пересчитываем деньги)
 * должна быть готова к моменту, когда счётчик появится.
 */
@Service
@RequiredArgsConstructor
@Log4j2
class ViewsSyncServiceImpl implements ViewsSyncService {

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final ViewCountProvider viewCountProvider;
    private final CampaignAccrualService campaignAccrualService;

    @Override
    @Transactional
    public int syncApproved() {
        List<Application> applications = getterApplication.getApproved();
        // Объявления собираем в множество: у одного объявления обычно несколько откликов,
        // а пересчёт бюджета в нём всё равно общий — гонять его на каждый отклик незачем
        Map<UUID, Campaign> touched = new LinkedHashMap<>();
        int updated = 0;

        for (Application application : applications) {
            Optional<Long> fetched = viewCountProvider.fetchViews(application.getPlatform(), application.getVideoUrl());
            if (fetched.isEmpty()) {
                continue;
            }
            long fresh = fetched.get();
            if (fresh < 0) {
                log.warn("Провайдер вернул отрицательные просмотры по отклику {}: {}", application.getPublicId(), fresh);
                continue;
            }
            long current = application.getViews() != null ? application.getViews() : 0L;
            if (fresh == current) {
                continue;
            }

            application.setViews(fresh);
            application.setViewsSyncedAt(Instant.now());
            saverApplication.save(application);
            touched.put(application.getCampaign().getId(), application.getCampaign());
            updated++;
        }

        touched.values().forEach(campaignAccrualService::recalculate);
        log.info("Синхронизация просмотров: проверено откликов {}, обновлено {}, пересчитано объявлений {}",
                applications.size(), updated, touched.size());
        return updated;
    }
}
