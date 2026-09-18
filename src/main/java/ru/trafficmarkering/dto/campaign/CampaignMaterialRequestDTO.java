package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.campaign.MaterialKind;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Материал для криатора: загруженный файл или ссылка")
public class CampaignMaterialRequestDTO {

    @NotNull(message = "Укажите вид материала: FILE или LINK")
    @Schema(description = "FILE — файл из /api/files/campaign-material/presign, LINK — внешняя ссылка",
            required = true, example = "LINK")
    private MaterialKind kind;

    @Size(max = 255, message = "Название материала не длиннее 255 символов")
    @Schema(description = "Подпись; для файла по умолчанию — имя файла, для ссылки — сама ссылка",
            example = "Референсы")
    private String title;

    @Size(max = 2048, message = "Ссылка не длиннее 2048 символов")
    @Schema(description = "Адрес (только для LINK)", example = "https://disk.yandex.ru/d/abc")
    private String url;

    @Size(max = 512, message = "Ключ файла не длиннее 512 символов")
    @Schema(description = "Ключ загруженного файла (только для FILE)")
    private String fileKey;

    @Size(max = 255, message = "Тип файла не длиннее 255 символов")
    @Schema(description = "MIME-тип файла (только для FILE)", example = "application/pdf")
    private String contentType;

    @PositiveOrZero(message = "Размер файла не может быть отрицательным")
    @Schema(description = "Размер файла в байтах (только для FILE)", example = "1048576")
    private Long sizeBytes;
}
