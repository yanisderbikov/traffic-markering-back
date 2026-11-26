package ru.trafficmarkering.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import ru.trafficmarkering.dto.auth.TelegramAuthStartDTO;
import ru.trafficmarkering.dto.studio.StudioTelegramLinkDTO;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Из-за @EnableWebMvc ответы пишет маппер без настроек Spring Boot — по умолчанию он отдаёт даты числами.
 * Фронт ждёт ISO-строку, поэтому у Instant в DTO стоит @JsonFormat(STRING).
 */
class DateSerializationTest {

    private final com.fasterxml.jackson.databind.ObjectMapper mvcMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void instantsGoOutAsIsoStrings() throws Exception {
        Instant at = Instant.parse("2026-11-23T19:00:00Z");
        assertThat(mvcMapper.writeValueAsString(new TelegramAuthStartDTO("u", "s", at)))
                .contains("\"expiresAt\":\"2026-11-23T19:00:00Z\"");
        assertThat(mvcMapper.writeValueAsString(new StudioTelegramLinkDTO("u", at)))
                .contains("\"2026-11-23T19:00:00Z\"");
    }
}
