package ru.trafficmarkering.model.campaign;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ViewRegionTest {

    @Test
    void russiaCoversOnlyRussiaIgnoringCase() {
        assertThat(ViewRegion.RUSSIA.covers("RU")).isTrue();
        assertThat(ViewRegion.RUSSIA.covers("ru")).isTrue();
        assertThat(ViewRegion.RUSSIA.covers("KZ")).isFalse();
        assertThat(ViewRegion.RUSSIA.covers("US")).isFalse();
        assertThat(ViewRegion.RUSSIA.covers(null)).isFalse();
    }

    @Test
    void cisCoversMemberStatesOnly() {
        for (String country : new String[]{"RU", "BY", "KZ", "KG", "TJ", "UZ", "AM", "AZ", "TM", "MD"}) {
            assertThat(ViewRegion.CIS.covers(country)).as(country).isTrue();
        }
        assertThat(ViewRegion.CIS.covers("UA")).isFalse();
        assertThat(ViewRegion.CIS.covers("GE")).isFalse();
        assertThat(ViewRegion.CIS.covers("US")).isFalse();
        assertThat(ViewRegion.CIS.covers("ZZ")).isFalse();
        assertThat(ViewRegion.CIS.covers(null)).isFalse();
    }

    @Test
    void worldCoversAnything() {
        assertThat(ViewRegion.WORLD.isWorld()).isTrue();
        assertThat(ViewRegion.WORLD.covers("US")).isTrue();
        assertThat(ViewRegion.WORLD.covers("ZZ")).isTrue();
        assertThat(ViewRegion.WORLD.covers(null)).isTrue();
        assertThat(ViewRegion.RUSSIA.isWorld()).isFalse();
        assertThat(ViewRegion.CIS.isWorld()).isFalse();
    }

    @Test
    void viewsWithinSumsOnlyCoveredCountries() {
        Map<String, Long> countryViews = Map.of("RU", 800L, "KZ", 100L, "US", 50L, "ZZ", 25L);

        assertThat(ViewRegion.RUSSIA.viewsWithin(countryViews)).isEqualTo(800L);
        assertThat(ViewRegion.CIS.viewsWithin(countryViews)).isEqualTo(900L);
        assertThat(ViewRegion.WORLD.viewsWithin(countryViews)).isEqualTo(975L);
    }

    @Test
    void viewsWithinIgnoresNullValues() {
        Map<String, Long> countryViews = new HashMap<>();
        countryViews.put("RU", 300L);
        countryViews.put("BY", null);

        assertThat(ViewRegion.CIS.viewsWithin(countryViews)).isEqualTo(300L);
        assertThat(ViewRegion.WORLD.viewsWithin(countryViews)).isEqualTo(300L);
    }

    @Test
    void viewsWithinNullMapIsZero() {
        assertThat(ViewRegion.RUSSIA.viewsWithin(null)).isZero();
        assertThat(ViewRegion.WORLD.viewsWithin(null)).isZero();
    }
}
