package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import ru.trafficmarkering.model.Role;

@Schema(description = "Начало входа через Telegram. Роль — только со страницы регистрации")
public record TelegramAuthStartRequestDTO(
        @Schema(description = "CUSTOMER или CREATOR для новой учётки; пусто — бот спросит сам") Role role,
        @Size(max = 64) String referralCode
) {
}
