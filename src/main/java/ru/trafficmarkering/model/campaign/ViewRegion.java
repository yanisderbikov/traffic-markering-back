package ru.trafficmarkering.model.campaign;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum ViewRegion {
    RUSSIA("Только РФ", Set.of("RU")),
    CIS("СНГ", Set.of("RU", "BY", "KZ", "KG", "TJ", "UZ", "AM", "AZ", "TM", "MD")),
    WORLD("Весь мир", Set.of());

    private final String description;
    private final Set<String> countries;

    ViewRegion(String description, Set<String> countries) {
        this.description = description;
        this.countries = countries;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getCountries() {
        return countries;
    }

    public boolean isWorld() {
        return this == WORLD;
    }

    public boolean covers(String countryCode) {
        if (isWorld()) {
            return true;
        }
        return countryCode != null && countries.contains(countryCode.toUpperCase(Locale.ROOT));
    }

    public long viewsWithin(Map<String, Long> countryViews) {
        if (countryViews == null) {
            return 0L;
        }
        return countryViews.entrySet().stream()
                .filter(entry -> entry.getValue() != null && covers(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }
}
