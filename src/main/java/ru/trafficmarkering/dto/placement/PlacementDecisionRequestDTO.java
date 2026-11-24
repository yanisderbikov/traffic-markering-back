package ru.trafficmarkering.dto.placement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.application.PlacementState;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Решение модератора или заказчика по одной надбавке")
public class PlacementDecisionRequestDTO {

    @NotNull(message = "Решение обязательно")
    @Schema(description = "VERIFIED — подтвердить, REJECTED — не подтверждать (нужна причина)", required = true)
    private PlacementState decision;

    @Schema(description = "Причина отказа; её увидит криатор")
    private String reason;
}
