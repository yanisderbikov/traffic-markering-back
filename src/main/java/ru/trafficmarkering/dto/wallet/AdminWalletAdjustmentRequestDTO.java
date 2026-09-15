package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Ручная корректировка кошелька менеджером")
public record AdminWalletAdjustmentRequestDTO(
        @NotNull
        @Schema(description = "Signed сумма в копейках: плюс — пополнение, минус — списание", example = "150000")
        Long amountKopecks,
        @NotBlank(message = "Укажите причину изменения баланса")
        @Size(max = 500, message = "Причина не должна превышать 500 символов")
        String reason
) {
}
