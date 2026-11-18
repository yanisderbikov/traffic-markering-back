package ru.trafficmarkering.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "Финансист отметил, что USDT отправлены: номер транзакции и скриншоты")
public class TransferSentRequestDTO {

    @NotBlank(message = "Укажите номер транзакции")
    @Size(max = 255, message = "Номер транзакции не длиннее 255 символов")
    @Schema(description = "Номер (хеш) транзакции в сети TRON", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "7c1e0f…9a2b")
    private String txId;

    @NotEmpty(message = "Приложите хотя бы один скриншот")
    @Size(max = 10, message = "Не больше 10 скриншотов")
    @Schema(description = "Ключи скриншотов из /api/files/transfer-proof/presign", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Size(max = 512) String> proofKeys;

    @Size(max = 2000, message = "Комментарий не длиннее 2000 символов")
    @Schema(description = "Ссылка на транзакцию в обозревателе, пояснение", example = "https://tronscan.org/#/transaction/…")
    private String comment;
}
