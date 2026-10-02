package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverViewSnapshot;
import ru.trafficmarkering.service.fraud.FraudCheckService;
import ru.trafficmarkering.service.views.VideoMetrics;
import ru.trafficmarkering.service.views.ViewCount;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ViewsRecorder {

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final SaverViewSnapshot saverViewSnapshot;
    private final FraudCheckService fraudCheckService;

    @Transactional
    public Optional<Recorded> record(UUID applicationId, ViewCount fresh, ViewSource source, Instant capturedAt) {
        Optional<Application> locked = getterApplication.getByIdForUpdate(applicationId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        Application application = locked.get();
        VideoMetrics metrics = fresh.metrics();
        saverViewSnapshot.save(ApplicationViewSnapshot.builder()
                .application(application)
                .capturedAt(capturedAt)
                .views(fresh.total())
                .countryViews(fresh.byCountry())
                .source(source)
                .likes(metrics.likes())
                .comments(metrics.comments())
                .shares(metrics.shares())
                .saves(metrics.saves())
                .reach(metrics.reach())
                .engagedViews(metrics.engagedViews())
                .avgWatchSeconds(metrics.avgWatchSeconds())
                .avgViewPercentage(metrics.avgViewPercentage())
                .trafficSources(metrics.trafficSources())
                .build());
        if (metrics.publishedAt() != null && application.getVideoPublishedAt() == null) {
            application.setVideoPublishedAt(metrics.publishedAt());
        }

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

        boolean fraudChanged = fraudCheckService.check(application);
        return Optional.of(new Recorded(application.getCampaign().getId(),
                effective != current || geographyChanged || fraudChanged));
    }

    record Recorded(UUID campaignId, boolean accrualAffected) {
    }
}
