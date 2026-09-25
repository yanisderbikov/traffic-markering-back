package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Заказчик перевёл USDT по заявке: скриншоты или файлы перевода и, если есть, номер транзакции")
public class TopUpPaidRequestDTO {

    @Size(max = 255, message = "Номер транзакции не длиннее 255 символов")
    @Schema(description = "Номер (хеш) транзакции в сети TRON; необязателен, но ускоряет проверку", example = "7c1e0f…9a2b")
    private String txId;

    @NotEmpty(message = "Приложите хотя бы один скриншот или файл перевода")
    @Size(max = 10, message = "Не больше 10 файлов")
    @Schema(description = "Ключи файлов из /api/files/transfer-proof/presign", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Size(max = 512) String> proofKeys;
}
