package ru.trafficmarkering.dto.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Менеджеры модерации и состояние очереди")
public record ModeratorsDTO(
        @Schema(description = "Бот модерации настроен (есть токен)") boolean botEnabled,
        @Schema(description = "Ник бота без @, куда заходят менеджеры") String botUsername,
        @Schema(description = "Сколько минут заявка висит у менеджера") int holdMinutes,
        @Schema(description = "Роликов на модерации, которые ни у кого сейчас не висят") long waiting,
        List<ModeratorDTO> moderators
) {
}
