package ru.trafficmarkering.service.social.impl;

import java.util.List;
import java.util.Map;

final class SocialJson {

    private SocialJson() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Map<String, Object> source, String key) {
        if (source == null) {
            return Map.of();
        }
        Object value = source.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Map<String, Object> source, String key) {
        if (source == null) {
            return List.of();
        }
        Object value = source.get(key);
        return value instanceof List ? (List<Object>) value : List.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> firstObject(List<Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Object value = source.get(0);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    static String text(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    static Long number(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
