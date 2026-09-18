package ru.trafficmarkering.model.application;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CountryViewsConverterTest {

    private final CountryViewsConverter converter = new CountryViewsConverter();

    @Test
    void roundTripKeepsCountries() {
        Map<String, Long> countryViews = Map.of("RU", 12_000L, "KZ", 300L);

        String json = converter.convertToDatabaseColumn(countryViews);
        Map<String, Long> restored = converter.convertToEntityAttribute(json);

        assertThat(restored).containsExactlyInAnyOrderEntriesOf(countryViews);
    }

    @Test
    void nullAndBlankMeanUnknownGeography() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
        assertThat(CountryViewsConverter.normalize(null)).isNull();
    }

    @Test
    void normalizeUppercasesAndTrimsKeys() {
        Map<String, Long> normalized = CountryViewsConverter.normalize(Map.of("ru", 100L, " kz ", 5L));

        assertThat(normalized).containsExactly(Map.entry("KZ", 5L), Map.entry("RU", 100L));
    }

    @Test
    void normalizeMergesKeysDifferingOnlyByCase() {
        Map<String, Long> normalized = CountryViewsConverter.normalize(Map.of("ru", 100L, "RU", 50L));

        assertThat(normalized).containsExactly(Map.entry("RU", 150L));
    }

    @Test
    void normalizeDropsNegativeNullAndNullKeys() {
        Map<String, Long> countryViews = new HashMap<>();
        countryViews.put("RU", 100L);
        countryViews.put("US", -1L);
        countryViews.put("BY", null);
        countryViews.put(null, 7L);

        Map<String, Long> normalized = CountryViewsConverter.normalize(countryViews);

        assertThat(normalized).containsExactly(Map.entry("RU", 100L));
    }

    @Test
    void emptyMapStaysEmptyAndKnown() {
        String json = converter.convertToDatabaseColumn(Map.of());

        assertThat(json).isEqualTo("{}");
        assertThat(converter.convertToEntityAttribute(json)).isEmpty();
    }
}
