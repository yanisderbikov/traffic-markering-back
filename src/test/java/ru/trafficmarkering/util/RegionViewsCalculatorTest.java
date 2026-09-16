package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.campaign.Region;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionViewsCalculatorTest {

    @Test
    void viewsForRegion_russiaCountsOnlyRu() {
        Map<String, Long> byCountry = Map.of("RU", 700L, "BY", 200L, "US", 100L);
        assertEquals(700L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, byCountry));
    }

    @Test
    void viewsForRegion_cisIncludesRussiaAndNeighbours() {
        Map<String, Long> byCountry = Map.of("RU", 500L, "BY", 300L, "KZ", 100L, "US", 1000L);
        assertEquals(900L, RegionViewsCalculator.viewsForRegion(Region.CIS, byCountry));
    }

    @Test
    void viewsForRegion_worldwideCountsEverything() {
        Map<String, Long> byCountry = Map.of("RU", 500L, "US", 1000L, "BR", 250L);
        assertEquals(1750L, RegionViewsCalculator.viewsForRegion(Region.WORLDWIDE, byCountry));
    }

    @Test
    void viewsForRegion_missingBreakdownIsZero() {
        assertEquals(0L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, Map.of()));
        assertEquals(0L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, null));
    }

    @Test
    void viewsForRegion_caseInsensitiveCountryCode() {
        Map<String, Long> byCountry = Map.of("ru", 400L);
        assertEquals(400L, RegionViewsCalculator.viewsForRegion(Region.RUSSIA, byCountry));
    }
}
