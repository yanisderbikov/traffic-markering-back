package ru.trafficmarkering.model.fraud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/** Список флагов хранится в TEXT как JSON-массив: отдельная таблица ради подсказок не нужна. */
@Converter
public class FraudFlagsConverter implements AttributeConverter<List<FraudFlag>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<FraudFlag>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<FraudFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(flags);
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось сериализовать флаги антифрода", e);
        }
    }

    @Override
    public List<FraudFlag> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось прочитать флаги антифрода: " + json, e);
        }
    }
}
