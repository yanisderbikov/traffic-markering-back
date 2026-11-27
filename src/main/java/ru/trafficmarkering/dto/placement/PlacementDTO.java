package ru.trafficmarkering.dto.placement;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.application.Placement;
import ru.trafficmarkering.model.application.PlacementKind;

@Schema(description = "Одна надбавка работы «ссылка и промокод»: состояние, доказательство, автопроверка, решение")
public record PlacementDTO(
        @Schema(description = "LINK — ссылка в профиле, PROMO — промокод в закрепе") String kind,
        @Schema(description = "Человекочитаемая надбавка", example = "Ссылка в профиле") String kindDescription,
        @Schema(description = "Предлагает ли объявление эту надбавку") boolean offered,
        @Schema(description = "NOT_SELECTED, PENDING, VERIFIED, EARNED, REJECTED") String state,
        @Schema(description = "Человекочитаемое состояние", example = "Ждёт проверки") String stateDescription,
        @Schema(description = "Временная ссылка на скриншот или запись экрана; null — не приложено") String proofFileUrl,
        @Schema(description = "Ключ доказательства в хранилище") String proofKey,
        @Schema(description = "Ссылка от креатора: на закреплённый комментарий или профиль") String proofUrl,
        @Schema(description = "Автопроверка: FOUND, MISSING, UNKNOWN; null — не проводилась") String checkState,
        @Schema(description = "Человекочитаемый итог автопроверки") String checkStateDescription,
        @Schema(description = "Что увидела автопроверка") String checkDetail,
        @Schema(description = "Когда прошла автопроверка, ISO-8601") String checkedAt,
        @Schema(description = "Причина отказа или пометка системы") String note,
        @Schema(description = "Когда принято решение, ISO-8601") String decidedAt
) {
    public static PlacementDTO from(PlacementKind kind, boolean offered, Placement placement, String proofFileUrl) {
        return new PlacementDTO(
                kind.name(),
                kind.getDescription(),
                offered,
                placement.state().name(),
                placement.state().getDescription(),
                proofFileUrl,
                placement.getProofKey(),
                placement.getProofUrl(),
                placement.getCheckState() != null ? placement.getCheckState().name() : null,
                placement.getCheckState() != null ? placement.getCheckState().getDescription() : null,
                placement.getCheckDetail(),
                placement.getCheckedAt() != null ? placement.getCheckedAt().toString() : null,
                placement.getNote(),
                placement.getDecidedAt() != null ? placement.getDecidedAt().toString() : null);
    }
}
