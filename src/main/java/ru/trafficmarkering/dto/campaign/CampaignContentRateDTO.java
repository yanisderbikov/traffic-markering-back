package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.trafficmarkering.model.campaign.CampaignContentRate;

@Schema(description = "Тематика ролика со своей ставкой")
public record CampaignContentRateDTO(
        @NotBlank(message = "Название тематики обязательно")
        @Size(max = CampaignContentRate.MAX_NAME_LENGTH, message = "Название тематики не длиннее 64 символов")
        @Schema(description = "Тематика ролика, как её назвал заказчик", example = "CS и Dota 2")
        String name,

        @NotNull(message = "Укажите ставку для тематики")
        @Positive(message = "Ставка тематики должна быть больше нуля")
        @Schema(description = "Ставка за 1000 просмотров, в копейках", example = "11500")
        Long ratePerThousandKopecks,

        @Size(max = 32, message = "Код тематики не длиннее 32 символов")
        @Schema(description = "Код тематики из /api/campaigns/topics; если задан, название берётся из справочника. "
                + "null — тематика названа от руки", example = "GAMING")
        String topicCode
) {
    public static CampaignContentRateDTO from(CampaignContentRate rate) {
        return new CampaignContentRateDTO(rate.getName(), rate.getRatePerThousandKopecks(), rate.getTopicCode());
    }
}
