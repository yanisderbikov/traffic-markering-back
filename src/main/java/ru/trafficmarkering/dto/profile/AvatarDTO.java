package ru.trafficmarkering.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Аватарка текущего пользователя")
public record AvatarDTO(
        @Schema(description = "Временная ссылка на картинку; null — аватарки нет") String avatarUrl
) {
}
