package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.campaign.CampaignTopic;

@Schema(description = "Тематика объявления со средней ставкой по ней")
public record CampaignTopicDTO(
        @Schema(description = "Код тематики; его передают в topic при сохранении объявления", example = "TECH")
        String code,
        @Schema(description = "Название тематики", example = "Технологии и гаджеты") String description,
        @Schema(description = "Средняя ставка за 1000 просмотров по тематике, в копейках; "
                + "null — неизвестна: тематику добавил заказчик", example = "20000")
        Long averageRatePerThousandKopecks
) {
    public static CampaignTopicDTO from(CampaignTopic topic) {
        return new CampaignTopicDTO(topic.getCode(), topic.getName(), topic.getAverageRatePerThousandKopecks());
    }
}
