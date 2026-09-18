package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.service.views.ViewsSyncService;

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
    private final CampaignAccrualService campaignAccrualService;

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

            Map<String, ViewCount> fetched;
            try {
                fetched = provider.get().fetchViews(group.getKey().creatorId(), urls);
            } catch (Exception e) {
                log.warn("Не удалось получить просмотры {} для криатора {}: {}",
                        group.getKey().platform(), group.getKey().creatorId(), e.getMessage());
                continue;
            }

            for (Application application : group.getValue()) {
                ViewCount fresh = fetched.get(application.getVideoUrl());
                if (fresh == null || fresh.total() < 0) {
                    continue;
                }
                saverViewSnapshot.save(ApplicationViewSnapshot.builder()
                        .application(application)
                        .capturedAt(capturedAt)
                        .views(fresh.total())
                        .countryViews(fresh.byCountry())
                        .source(provider.get().source())
                        .build());
                captured++;

                long current = application.totalViews();
                long effective = Math.max(current, fresh.total());
                boolean geographyChanged = fresh.geographyKnown()
                        && !Objects.equals(application.getCountryViews(), fresh.byCountry());
                application.setViews(effective);
                if (fresh.geographyKnown()) {
                    application.setCountryViews(fresh.byCountry());
                }
                application.setViewsSyncedAt(capturedAt);
                saverApplication.save(application);

                if (effective != current || geographyChanged) {
                    touched.put(application.getCampaign().getId(), application.getCampaign());
                }
            }
        }

        touched.values().forEach(campaignAccrualService::recalculate);
        log.info("Синхронизация просмотров: откликов {}, снимков {}, пересчитано объявлений {}",
                applications.size(), captured, touched.size());
        return captured;
    }

    private record GroupKey(Platform platform, Long creatorId) {
    }
}
