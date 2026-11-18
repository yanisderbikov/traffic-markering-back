package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Объявление в своём сегменте — среди других запущенных объявлений той же тематики")
public record CampaignSegmentDTO(
        @Schema(description = "Код тематики; null — тематика не выбрана, сегмента нет", example = "TECH")
        String topic,
        @Schema(description = "Название тематики", example = "Технологии и гаджеты") String topicDescription,
        @Schema(description = "Сколько других запущенных объявлений в тематике", example = "12")
        long campaignsCount,
        @Schema(description = "Хватает ли объявлений для сравнения; если нет — медианы и проценты пустые")
        boolean comparable,
        @Schema(description = "С какого числа других объявлений тематики сравниваем", example = "3")
        int minCampaignsCount,
        CampaignSegmentMetricDTO rate,
        CampaignSegmentMetricDTO budget,
        @Schema(description = "Средняя рыночная ставка за 1000 просмотров по тематике, в копейках; "
                + "null — неизвестна: тематику добавил заказчик", example = "20000")
        Long marketRatePerThousandKopecks,
        @Schema(description = "На сколько процентов ставка выше рыночной по тематике, со знаком; null — не с чем сравнить",
                example = "-10")
        Integer marketRateDiffPercent
) {
}
