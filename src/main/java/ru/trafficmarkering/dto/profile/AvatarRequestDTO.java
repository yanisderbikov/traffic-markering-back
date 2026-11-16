package ru.trafficmarkering.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Сохранение аватарки")
public record AvatarRequestDTO(
        @NotBlank(message = "Ключ файла обязателен")
        @Size(max = 255, message = "Ключ файла не длиннее 255 символов")
        @Schema(description = "key из ответа POST /api/profile/avatar/presign", required = true)
        String key
) {
}
