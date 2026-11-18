package ru.trafficmarkering.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Отклонение операции: деньги возвращаются туда, откуда ушли")
public class TransferRejectRequestDTO {

    @NotBlank(message = "Укажите причину отклонения")
    @Size(max = 1000, message = "Причина не длиннее 1000 символов")
    @Schema(description = "Причина, которую увидит владелец кошелька", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Адрес не похож на TRC-20, уточните кошелёк")
    private String reason;
}
