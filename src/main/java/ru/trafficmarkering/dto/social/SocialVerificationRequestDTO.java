package ru.trafficmarkering.dto.social;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Аккаунт, который криатор хочет привязать по проверочному коду")
public record SocialVerificationRequestDTO(
        @Schema(description = "Ник (с @ или без) или ссылка на профиль; для YouTube ещё id канала UC…",
                example = "@my_channel")
        @NotBlank(message = "Укажите ник или ссылку на профиль")
        @Size(max = 300, message = "Слишком длинная ссылка")
        String account
) {
}
