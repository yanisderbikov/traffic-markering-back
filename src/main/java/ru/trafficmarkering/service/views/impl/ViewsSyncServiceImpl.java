package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Region;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.geo.GeoAnalyticsProvider;
import ru.trafficmarkering.service.geo.VideoGeoViews;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.service.views.ViewsSyncService;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
class ViewsSyncServiceImpl implements ViewsSyncService {

    private final GetterApplication getterApplication;
    private final SaverViewSnapshot saverViewSnapshot;
    private final ViewCountProviders viewCountProviders;
    private final GeoAnalyticsProvider geoAnalyticsProvider;
    private final ViewsSyncResultWriter resultWriter;

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
        Set<UUID> touched = new LinkedHashSet<>();
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

                if (resultWriter.applyViews(application, fresh, capturedAt)) {
                    touched.add(application.getCampaign().getId());
                }
            }
        }

        if (geoAnalyticsProvider.isConfigured()) {
            syncRegionViews(groups, touched);
        }

        touched.forEach(resultWriter::recalculate);
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
    private void syncRegionViews(Map<GroupKey, List<Application>> groups, Set<UUID> touched) {
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
                if (resultWriter.applyGeoViews(application, geoViews)) {
                    touched.add(application.getCampaign().getId());
                }
            }
        }
    }

    private record GroupKey(Platform platform, Long creatorId) {
    }
}
