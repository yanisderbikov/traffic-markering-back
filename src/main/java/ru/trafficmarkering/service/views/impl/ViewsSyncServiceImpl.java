package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.Region;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.geo.GeoAnalyticsProvider;
import ru.trafficmarkering.service.geo.VideoGeoViews;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.service.views.ViewsSyncService;
import ru.trafficmarkering.util.RegionViewsCalculator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
class ViewsSyncServiceImpl implements ViewsSyncService {

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final SaverViewSnapshot saverViewSnapshot;
    private final ViewCountProviders viewCountProviders;
    private final GeoAnalyticsProvider geoAnalyticsProvider;
    private final CampaignAccrualService campaignAccrualService;
    private final GetterCampaign getterCampaign;

    @Override
    public int syncApproved() {
        List<Application> applications = getterApplication.getApproved();
        if (applications.isEmpty()) {
            return 0;
        }

        Map<GroupKey, List<Application>> groups = applications.stream()
                .filter(application -> application.getPlatform() != null && application.getCreator() != null)
                .collect(Collectors.groupingBy(
                        application -> new GroupKey(application.getPlatform(), application.getCreator().getId())));

        Instant capturedAt = Instant.now();
        Map<UUID, Campaign> touched = new LinkedHashMap<>();
        int captured = 0;

        for (Map.Entry<GroupKey, List<Application>> group : groups.entrySet()) {
            Optional<ViewCountProvider> provider = viewCountProviders.forPlatform(group.getKey().platform());
            if (provider.isEmpty()) {
                continue;
            }

            List<String> urls = group.getValue().stream()
                    .map(Application::getVideoUrl)
                    .distinct()
                    .toList();

            Map<String, Long> fetched;
            try {
                fetched = provider.get().fetchViews(group.getKey().creatorId(), urls);
            } catch (Exception e) {
                log.warn("Не удалось получить просмотры {} для криатора {}: {}",
                        group.getKey().platform(), group.getKey().creatorId(), e.getMessage());
                continue;
            }

            for (Application application : group.getValue()) {
                Long fresh = fetched.get(application.getVideoUrl());
                if (fresh == null || fresh < 0) {
                    continue;
                }
                saverViewSnapshot.save(ApplicationViewSnapshot.builder()
                        .application(application)
                        .capturedAt(capturedAt)
                        .views(fresh)
                        .source(provider.get().source())
                        .build());
                captured++;

                long current = application.getViews() != null ? application.getViews() : 0L;
                long effective = Math.max(current, fresh);
                application.setViews(effective);
                application.setViewsSyncedAt(capturedAt);
                saverApplication.save(application);

                if (effective != current) {
                    touched.put(application.getCampaign().getId(), application.getCampaign());
                }
            }
        }

        if (geoAnalyticsProvider.isConfigured()) {
            syncRegionViews(groups, touched);
        }

        touched.values().forEach(campaignAccrualService::recalculate);
        log.info("Синхронизация просмотров: откликов {}, снимков {}, пересчитано объявлений {}",
                applications.size(), captured, touched.size());
        return captured;
    }

    /**
     * Отдельный проход обновляет regionViews для откликов на региональные офферы
     * (RUSSIA/CIS) — WORLDWIDE в геоаналитике не нуждается, там платят за все просмотры.
     * Пока провайдер не настроен, syncApproved его вообще не вызывает — начисление по
     * региону остаётся явно «на паузе», а не рискует тихо разъехаться на пустых ответах.
     */
    private void syncRegionViews(Map<GroupKey, List<Application>> groups, Map<UUID, Campaign> touched) {
        for (Map.Entry<GroupKey, List<Application>> group : groups.entrySet()) {
            List<Application> regional = group.getValue().stream()
                    .filter(application -> application.getCampaign().getRegion() != Region.WORLDWIDE)
                    .toList();
            if (regional.isEmpty()) {
                continue;
            }

            List<String> urls = regional.stream().map(Application::getVideoUrl).distinct().toList();
            Map<String, VideoGeoViews> fetched;
            try {
                fetched = geoAnalyticsProvider.fetchGeoViews(group.getKey().platform(), group.getKey().creatorId(), urls);
            } catch (Exception e) {
                log.warn("Не удалось получить геоаналитику {} для криатора {}: {}",
                        group.getKey().platform(), group.getKey().creatorId(), e.getMessage());
                continue;
            }

            for (Application application : regional) {
                VideoGeoViews geoViews = fetched.get(application.getVideoUrl());
                if (geoViews == null) {
                    continue;
                }
                // fetchGeoViews — внешний вызов, за время которого заказчик мог сменить регион
                // (и applications выше загружены до него). Регион перепроверяем отдельным
                // скалярным запросом прямо перед сохранением, а не доверяем той ссылке, что
                // держит application.getCampaign(), — иначе можно применить разбивку, посчитанную
                // под уже неактуальный регион, и воскресить значение, которое смена региона
                // должна была обнулить.
                Region freshRegion = getterCampaign.getRegionById(application.getCampaign().getId()).orElse(null);
                if (freshRegion == null || freshRegion == Region.WORLDWIDE) {
                    continue;
                }
                long rawViews = application.getViews() != null ? application.getViews() : 0L;
                long regionViews = RegionViewsCalculator.viewsForRegion(
                        freshRegion, geoViews.viewsByCountry(), rawViews);
                if (!Objects.equals(application.getRegionViews(), regionViews)) {
                    application.setRegionViews(regionViews);
                    saverApplication.save(application);
                    touched.put(application.getCampaign().getId(), application.getCampaign());
                }
            }
        }
    }

    private record GroupKey(Platform platform, Long creatorId) {
    }
}
