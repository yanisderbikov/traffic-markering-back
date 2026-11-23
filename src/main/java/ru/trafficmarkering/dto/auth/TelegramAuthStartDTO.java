package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Попытка входа через Telegram: ссылка в бота и секрет, по которому браузер ждёт результат")
public record TelegramAuthStartDTO(
        @Schema(description = "Ссылка t.me на бота с кодом входа") String url,
        @Schema(description = "Секрет попытки — хранится только в этом браузере") String secret,
        Instant expiresAt
) {
}
