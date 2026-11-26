package ru.trafficmarkering.model.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Converter
public class CountryViewsConverter implements AttributeConverter<Map<String, Long>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Long>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Long> countryViews) {
        if (countryViews == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(normalize(countryViews));
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось сериализовать просмотры по странам", e);
        }
    }

    @Override
    public Map<String, Long> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return normalize(MAPPER.readValue(json, TYPE));
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось прочитать просмотры по странам: " + json, e);
        }
    }

    public static Map<String, Long> normalize(Map<String, Long> countryViews) {
        if (countryViews == null) {
            return null;
        }
        Map<String, Long> normalized = new TreeMap<>();
        countryViews.forEach((country, views) -> {
            if (country != null && views != null && views >= 0) {
                normalized.merge(country.trim().toUpperCase(Locale.ROOT), views, Long::sum);
            }
        });
        return normalized;
    }
}
