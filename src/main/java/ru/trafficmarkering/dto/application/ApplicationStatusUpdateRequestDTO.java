package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.application.ApplicationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Решение по отклику: заказчика или менеджера на модерации")
public class ApplicationStatusUpdateRequestDTO {

    @NotNull(message = "Статус обязателен")
    @Schema(description = "Новый статус: APPROVED, REJECTED или COMPLETED",
            required = true, example = "APPROVED")
    private ApplicationStatus status;

    @Size(max = 2000, message = "Причина отказа не длиннее 2000 символов")
    @Schema(description = "Причина отказа: обязательна для REJECTED, её видит криатор", example = "Ролик не по брифу")
    private String reason;
}
