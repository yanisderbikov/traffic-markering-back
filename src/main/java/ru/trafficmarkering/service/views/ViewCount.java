package ru.trafficmarkering.service.views;

import ru.trafficmarkering.model.application.CountryViewsConverter;

import java.util.Map;

public record ViewCount(long total, Map<String, Long> byCountry) {

    public static ViewCount total(long total) {
        return new ViewCount(total, null);
    }

    public static ViewCount withCountries(long total, Map<String, Long> byCountry) {
        return new ViewCount(total, CountryViewsConverter.normalize(byCountry));
    }

    public boolean geographyKnown() {
        return byCountry != null;
    }
}
