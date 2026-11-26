package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Состояние входа через Telegram")
public record TelegramAuthPollDTO(
        @Schema(description = "PENDING — ждём бота; DONE — вход выполнен, токен в auth; "
                + "NEED_EMAIL — Telegram подтверждён, но учётки нет: войдите по почте, и он привяжется; "
                + "CANCELLED — отменено в боте; EXPIRED — попытка устарела") String status,
        AuthResponseDTO auth,
        @Schema(description = "Имя или @ник Telegram для подсказки") String telegram
) {
    public static TelegramAuthPollDTO of(String status) {
        return new TelegramAuthPollDTO(status, null, null);
    }
}
