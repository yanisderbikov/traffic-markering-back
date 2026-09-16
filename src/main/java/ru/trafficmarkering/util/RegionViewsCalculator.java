package ru.trafficmarkering.util;

import ru.trafficmarkering.model.campaign.Region;

import java.util.Map;

/**
 * Сколько из просмотров ролика приходится на регион оффера — по разбивке из
 * {@link ru.trafficmarkering.service.geo.GeoAnalyticsProvider}. Просмотры без определённой
 * страны в сумму не попадают: платформа не платит за непроверенное гео.
 */
public final class RegionViewsCalculator {

    private RegionViewsCalculator() {
    }

    public static long viewsForRegion(Region region, Map<String, Long> viewsByCountry) {
        if (region == null || viewsByCountry == null || viewsByCountry.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<String, Long> entry : viewsByCountry.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0 && region.includesCountry(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
