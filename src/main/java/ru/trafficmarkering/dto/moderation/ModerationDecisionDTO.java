package ru.trafficmarkering.dto.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Решение менеджера по ролику в боте модерации")
public record ModerationDecisionDTO(
        long id,
        @Schema(description = "id отклика; null — криатор его потом удалил") UUID applicationId,
        String applicationPublicId,
        String campaignTitle,
        String creatorName,
        String videoUrl,
        @Schema(description = "Текущий статус отклика: решение могли потом поменять на сайте") String applicationStatus,
        @Schema(description = "Ник менеджера без @") String moderatorUsername,
        @Schema(description = "«@ivan_moder (Иван)»") String moderatorLabel,
        @Schema(description = "APPROVED или REJECTED") String decision,
        @Schema(description = "Причина отказа") String reason,
        @Schema(description = "Когда заявка пришла менеджеру") Instant assignedAt,
        @Schema(description = "Когда менеджер нажал кнопку") Instant decidedAt
) {
}
