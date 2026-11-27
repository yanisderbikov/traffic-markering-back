package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.repository.GetterApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.service.views.CapturedViews;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.service.views.ViewsSyncService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
class ViewsSyncServiceImpl implements ViewsSyncService {

    private final GetterApplication getterApplication;
    private final ViewCountProviders viewCountProviders;
    private final ViewsRecorder viewsRecorder;

    @Override
    public CapturedViews captureNow(UUID applicationId) {
        Application application = getterApplication.getById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отклик не найден: " + applicationId));
        if (application.getPlatform() == null || application.getVideoUrl() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "К отклику ещё не приложен ролик");
        }
        ViewCountProvider provider = viewCountProviders.forPlatform(application.getPlatform())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Подсчёт просмотров " + application.getPlatform().getDescription()
                                + " сейчас не настроен — напишите в поддержку"));
        Map<String, ViewCount> fetched;
        try {
            fetched = provider.fetchViews(application.getCreator().getId(), List.of(application.getVideoUrl()));
        } catch (Exception e) {
            log.warn("Не удалось снять просмотры отклика {}: {}", applicationId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Площадка не отдала просмотры ролика — попробуйте через пару минут");
        }
        ViewCount fresh = fetched.get(application.getVideoUrl());
        if (fresh == null || fresh.total() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Площадка не отдала просмотры ролика — попробуйте через пару минут");
        }
        return viewsRecorder.record(applicationId, fresh, provider.source(), Instant.now())
                .map(recorded -> new CapturedViews(recorded.snapshot()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отклик не найден: " + applicationId));
    }
}
