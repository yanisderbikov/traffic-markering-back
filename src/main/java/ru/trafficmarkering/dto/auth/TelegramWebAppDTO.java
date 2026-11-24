package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Результат входа из мини-приложения Telegram")
public record TelegramWebAppDTO(
        @Schema(description = "DONE — вход выполнен, токен в auth; NEED_ACCOUNT — Telegram ни к чему не привязан, "
                + "нужно выбрать action; NEED_EMAIL — войдите по почте и передайте secret в /telegram/attach")
        String status,
        AuthResponseDTO auth,
        @Schema(description = "Имя или @ник Telegram для подсказки") String telegram,
        @Schema(description = "Секрет для /telegram/attach при NEED_EMAIL") String secret,
        @Schema(description = "start_param из ссылки t.me/бот/app?startapp=…") String startParam
) {
}
