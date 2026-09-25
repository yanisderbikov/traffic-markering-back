package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Заявка заказчика на пополнение: сколько он собирается перевести")
public class TopUpCreateRequestDTO {

    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть больше нуля")
    @Schema(description = "Сумма в копейках", requiredMode = Schema.RequiredMode.REQUIRED, example = "5000000")
    private Long amountKopecks;
}
