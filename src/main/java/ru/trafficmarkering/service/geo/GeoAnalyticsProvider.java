package ru.trafficmarkering.service.geo;

import ru.trafficmarkering.model.application.Platform;

import java.util.Collection;
import java.util.Map;

/**
 * Источник геоаналитики по роликам: сколько всего просмотров и как они бьются по странам.
 * Источник истины — тот же анализатор площадки (Instagram/TikTok/YouTube Shorts), что
 * приносит и обычные просмотры в {@link ru.trafficmarkering.service.views.ViewCountProvider},
 * только с более детальным ответом.
 *
 * Интерфейс намеренно не знает о региональной группировке (РФ/СНГ/весь мир и т.п.) —
 * это отдельная ответственность выше по стеку, которая строится поверх сырых кодов стран.
 * Пока подключённого анализатора с гео нет, единственная реализация — заглушка,
 * которая явно сообщает об отсутствии данных через {@link #isConfigured()}, а не выдумывает их.
 */
public interface GeoAnalyticsProvider {

    /** Есть ли вообще смысл дёргать провайдер: настроен ли реальный источник гео-данных. */
    boolean isConfigured();

    /**
     * Просмотры с разбивкой по странам для роликов одного криатора на одной площадке.
     *
     * @param platform   площадка, на которой опубликованы ролики
     * @param creatorId  криатор — владелец роликов (нужен, если анализатору требуется его токен)
     * @param videoUrls  ссылки на ролики, по которым нужны данные
     * @return карта videoUrl → {@link VideoGeoViews}; ролики без данных в карту не попадают
     */
    Map<String, VideoGeoViews> fetchGeoViews(Platform platform, Long creatorId, Collection<String> videoUrls);
}
