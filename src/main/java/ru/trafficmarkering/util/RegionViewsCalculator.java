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

    /**
     * {@code rawTotalViews} — подтверждённое число просмотров ролика (то, что реально пойдёт
     * в начисление); гео-разбивка ему не подчинена технически, поэтому сумма по региону здесь
     * жёстко ограничивается этим значением — иначе кривой или устаревший ответ провайдера
     * (например, гео больше сырых просмотров) превратился бы в переплату. Сложение защищено
     * от переполнения: как только сумма достигает потолка, дальнейшие страны не добавляются.
     */
    public static long viewsForRegion(Region region, Map<String, Long> viewsByCountry, long rawTotalViews) {
        if (region == null || viewsByCountry == null || viewsByCountry.isEmpty()) {
            return 0L;
        }
        long cap = Math.max(0L, rawTotalViews);
        long total = 0L;
        for (Map.Entry<String, Long> entry : viewsByCountry.entrySet()) {
            if (total >= cap) {
                break;
            }
            Long value = entry.getValue();
            if (value == null || value <= 0 || !region.includesCountry(entry.getKey())) {
                continue;
            }
            total = value > cap - total ? cap : total + value;
        }
        return total;
    }
}
