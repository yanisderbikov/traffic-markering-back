package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ставка или бюджет объявления против других объявлений его тематики")
public record CampaignSegmentMetricDTO(
        @Schema(description = "Значение у этого объявления, в копейках; null — не заполнено", example = "18000")
        Long kopecks,
        @Schema(description = "Медиана по другим объявлениям тематики, в копейках; null — сравнивать не с чем",
                example = "15000")
        Long medianKopecks,
        @Schema(description = "На сколько процентов значение выше медианы, со знаком: −20 — ниже на 20%; "
                + "null — сравнивать не с чем", example = "20")
        Integer diffPercent,
        @Schema(description = "У какой доли других объявлений тематики значение ниже, в процентах; "
                + "null — сравнивать не с чем", example = "75")
        Integer higherThanPercent
) {
}
