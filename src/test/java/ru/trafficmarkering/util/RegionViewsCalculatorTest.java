package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.campaign.Region;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionViewsCalculatorTest {

    @Test
    void viewsForRegion_russiaCountsOnlyRu() {
        Map<String, Long> byCountry = Map.of("RU", 700L, "BY", 200L, "US", 100L);
        assertEquals(700L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, byCountry, 1_000L));
    }

    @Test
    void viewsForRegion_cisIncludesRussiaAndNeighbours() {
        Map<String, Long> byCountry = Map.of("RU", 500L, "BY", 300L, "KZ", 100L, "US", 1000L);
        assertEquals(900L, RegionViewsCalculator.viewsForRegion(Region.CIS, byCountry, 1_900L));
    }

    @Test
    void viewsForRegion_worldwideCountsEverything() {
        Map<String, Long> byCountry = Map.of("RU", 500L, "US", 1000L, "BR", 250L);
        assertEquals(1750L, RegionViewsCalculator.viewsForRegion(Region.WORLDWIDE, byCountry, 1_750L));
    }

    @Test
    void viewsForRegion_missingBreakdownIsZero() {
        assertEquals(0L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, Map.of(), 1_000L));
        assertEquals(0L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, null, 1_000L));
    }

    @Test
    void viewsForRegion_caseInsensitiveCountryCode() {
        Map<String, Long> byCountry = Map.of("ru", 400L);
        assertEquals(400L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, byCountry, 1_000L));
    }

    @Test
    void viewsForRegion_cappedToRawTotalViews() {
        // Гео отдал больше, чем реально насчитано просмотров ролика — переплаты быть не должно
        Map<String, Long> byCountry = Map.of("RU", 1_000_000L);
        assertEquals(50L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, byCountry, 50L));
    }

    @Test
    void viewsForRegion_negativeRawTotalTreatedAsZero() {
        Map<String, Long> byCountry = Map.of("RU", 100L);
        assertEquals(0L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, byCountry, -5L));
    }

    @Test
    void viewsForRegion_summationDoesNotOverflow() {
        Map<String, Long> byCountry = Map.of("RU", Long.MAX_VALUE, "BY", Long.MAX_VALUE);
        assertEquals(1_000L, RegionViewsCalculator.viewsForRegion(Region.CIS, byCountry, 1_000L));
    }
}
