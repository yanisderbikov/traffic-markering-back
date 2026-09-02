package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.campaign.CampaignStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Создание/обновление объявления")
public class CampaignCreateUpdateRequestDTO {

    @NotBlank(message = "Заголовок обязателен")
    @Size(max = 255, message = "Заголовок не длиннее 255 символов")
    @Schema(description = "Заголовок объявления", required = true, example = "Обзор приложения для доставки еды")
    private String title;

    @NotBlank(message = "Описание обязательно")
    @Schema(description = "Что нужно снять: формат, хронометраж, требования", required = true)
    private String description;

    @NotBlank(message = "Фотография обязательна")
    @Size(max = 512, message = "Ключ фотографии не длиннее 512 символов")
    @Schema(description = "Ключ загруженной фотографии из /api/files/campaign-photo/presign", required = true)
    private String photoKey;

    @NotNull(message = "Ставка обязательна")
    @Positive(message = "Ставка должна быть больше нуля")
    @Schema(description = "Ставка за 1000 просмотров, в копейках", required = true, example = "35000")
    private Long ratePerThousandKopecks;

    @NotNull(message = "Бюджет обязателен")
    @PositiveOrZero(message = "Бюджет не может быть отрицательным")
    @Schema(description = "Выделенный бюджет, в копейках", required = true, example = "5000000")
    private Long budgetKopecks;

    /** null — при создании берётся DRAFT, при обновлении статус не трогаем. */
    @Schema(description = "Статус; null — не менять (при создании DRAFT)", example = "ACTIVE")
    private CampaignStatus status;
}
