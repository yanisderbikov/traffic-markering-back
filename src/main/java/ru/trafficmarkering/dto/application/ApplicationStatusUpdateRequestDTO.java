package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.application.ApplicationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Решение заказчика по отклику")
public class ApplicationStatusUpdateRequestDTO {

    @NotNull(message = "Статус обязателен")
    @Schema(description = "Новый статус: APPROVED, REJECTED или COMPLETED",
            required = true, example = "APPROVED")
    private ApplicationStatus status;
}
