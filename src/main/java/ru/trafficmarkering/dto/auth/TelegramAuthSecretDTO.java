package ru.trafficmarkering.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelegramAuthSecretDTO(@NotBlank(message = "Нет секрета входа") @Size(max = 128) String secret) {
}
