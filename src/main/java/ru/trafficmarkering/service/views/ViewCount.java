package ru.trafficmarkering.service.views;

import ru.trafficmarkering.model.application.CountryViewsConverter;

import java.util.Map;

public record ViewCount(long total, Map<String, Long> byCountry, VideoMetrics metrics) {

    public static ViewCount total(long total) {
        return new ViewCount(total, null, VideoMetrics.empty());
    }

    public static ViewCount withCountries(long total, Map<String, Long> byCountry) {
        return new ViewCount(total, CountryViewsConverter.normalize(byCountry), VideoMetrics.empty());
    }

    public ViewCount withMetrics(VideoMetrics value) {
        return new ViewCount(total, byCountry, value != null ? value : VideoMetrics.empty());
    }

    public boolean geographyKnown() {
        return byCountry != null;
    }

    public VideoMetrics metrics() {
        return metrics != null ? metrics : VideoMetrics.empty();
    }
}
