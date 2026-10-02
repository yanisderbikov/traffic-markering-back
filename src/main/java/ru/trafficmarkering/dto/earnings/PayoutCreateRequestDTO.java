package ru.trafficmarkering.dto.earnings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Заявка криатора на вывод USDT (TRC-20)")
public class PayoutCreateRequestDTO {

    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть больше нуля")
    @Schema(description = "Сколько вывести, в копейках; не больше доступного",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "300000")
    private Long amountKopecks;

    @NotBlank(message = "Укажите адрес кошелька TRON")
    @Size(max = 64)
    @Schema(description = "Адрес кошелька TRON (TRC-20): начинается с T, 34 символа",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE")
    private String tronAddress;
}
