package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Вход из мини-приложения Telegram")
public record TelegramWebAppRequestDTO(
        @Schema(description = "Telegram.WebApp.initData как есть") @NotBlank String initData,
        @Schema(description = "Для Telegram без учётки: cr — новый креатор, cu — новый рекламодатель, "
                + "have — есть учётка с почтой") @Pattern(regexp = "cr|cu|have") String action,
        String referralCode
) {
}
