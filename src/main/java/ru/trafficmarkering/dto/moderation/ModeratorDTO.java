package ru.trafficmarkering.dto.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Менеджер модерации в Telegram")
public record ModeratorDTO(
        @Schema(description = "Ник Telegram без @") String username,
        @Schema(description = "false — убран из списка, остался ради истории") boolean enabled,
        @Schema(description = "Уже заходил в бота") boolean linked,
        @Schema(description = "Имя в Telegram") String displayName,
        @Schema(description = "Часы приёма, например «10:00–19:00 (UTC+3)»") String schedule,
        @Schema(description = "Активен: получает заявки в свои часы. Неактивен — выключился сам, выключил админ "
                + "или пропустил заявки подряд") boolean active,
        @Schema(description = "Прямо сейчас может получать заявки") boolean available,
        @Schema(description = "Номер ролика, который сейчас у менеджера") String activeApplication,
        @Schema(description = "До когда заявка у менеджера, ISO-8601") Instant activeUntil,
        @Schema(description = "Принято за всё время") long approved,
        @Schema(description = "Отклонено за всё время") long rejected,
        @Schema(description = "Пропущено (время вышло) за всё время") long missed,
        @Schema(description = "Решено за последние сутки") long decidedLastDay,
        @Schema(description = "Последний раз писал боту или решал заявку") Instant lastSeenAt
) {
}
