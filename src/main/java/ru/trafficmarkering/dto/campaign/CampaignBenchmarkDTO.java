package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Медианы по запущенным объявлениям площадки; пока объявлений нет — базовые значения")
public record CampaignBenchmarkDTO(
        @Schema(description = "Медианная ставка за 1000 просмотров, в копейках", example = "15000")
        long medianRatePerThousandKopecks,
        @Schema(description = "Медианный бюджет объявления, в копейках", example = "10000000")
        long medianBudgetKopecks
) {
}
