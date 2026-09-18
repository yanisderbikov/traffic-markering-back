package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Пополнение или вывод по кошельку заказчика: деньги уже переведены, финансист прикладывает документы")
public class WalletOperationRequestDTO {

    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть больше нуля")
    @Schema(description = "Сумма в копейках", requiredMode = Schema.RequiredMode.REQUIRED, example = "5000000")
    private Long amountKopecks;

    @NotBlank(message = "Укажите номер транзакции")
    @Size(max = 255, message = "Номер транзакции не длиннее 255 символов")
    @Schema(description = "Номер (хеш) транзакции в сети TRON", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "7c1e0f…9a2b")
    private String txId;

    @NotEmpty(message = "Приложите хотя бы один скриншот")
    @Size(max = 10, message = "Не больше 10 скриншотов")
    @Schema(description = "Ключи скриншотов из /api/files/transfer-proof/presign", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Size(max = 512) String> proofKeys;

    @Size(max = 64)
    @Schema(description = "Адрес TRON заказчика, куда ушли USDT; обязателен для вывода, для пополнения не нужен",
            example = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE")
    private String tronAddress;

    @Size(max = 500, message = "Основание не длиннее 500 символов")
    @Schema(description = "Основание: номер счёта, договор, пояснение", example = "Счёт №14 от 01.09")
    private String comment;
}
