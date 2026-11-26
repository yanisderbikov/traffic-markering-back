package ru.trafficmarkering.dto.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Статус менеджера модерации")
public record ModeratorActiveRequestDTO(
        @Schema(description = "true — активен, получает заявки; false — неактивен") boolean active
) {
}
