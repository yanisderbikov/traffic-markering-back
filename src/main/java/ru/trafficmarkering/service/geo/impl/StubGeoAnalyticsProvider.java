package ru.trafficmarkering.service.geo.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.geo.GeoAnalyticsProvider;
import ru.trafficmarkering.service.geo.VideoGeoViews;

import java.util.Collection;
import java.util.Map;

/**
 * Единственная реализация {@link GeoAnalyticsProvider} на сегодня: ни одна площадка
 * геоаналитику пока не отдаёт, поэтому провайдер всегда сообщает «данных нет»
 * ({@link #isConfigured()} == false, пустая карта) — и это штатное поведение, а не поломка,
 * как когда-то было с ManualViewCountProvider для обычных просмотров.
 *
 * Когда появится настоящий внешний анализатор с гео, он подставится вместо этого класса
 * отдельным {@code @Component}, реализующим тот же интерфейс — вызывающий код не изменится.
 */
@Component
@Log4j2
class StubGeoAnalyticsProvider implements GeoAnalyticsProvider {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public Map<String, VideoGeoViews> fetchGeoViews(Platform platform, Long creatorId, Collection<String> videoUrls) {
        log.debug("Геоаналитика для площадки {} пока недоступна, пропускаем {} роликов",
                platform, videoUrls.size());
        return Map.of();
    }
}
