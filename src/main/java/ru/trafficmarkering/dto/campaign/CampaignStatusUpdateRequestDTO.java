package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.campaign.CampaignStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Смена статуса объявления")
public class CampaignStatusUpdateRequestDTO {

    @NotNull(message = "Статус обязателен")
    @Schema(description = "Новый статус: DRAFT, ACTIVE, PAUSED или COMPLETED", required = true, example = "ACTIVE")
    private CampaignStatus status;
}
