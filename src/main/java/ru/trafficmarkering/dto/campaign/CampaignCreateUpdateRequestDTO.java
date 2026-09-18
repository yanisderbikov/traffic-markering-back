package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.campaign.ViewRegion;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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

    @NotNull(message = "Порог вывода обязателен")
    @Positive(message = "Порог вывода должен быть больше нуля")
    @Schema(description = "С какой накопленной по объявлению суммы криатор может выводить заработанное, в копейках",
            required = true, example = "300000")
    private Long minPayoutKopecks;

    @NotEmpty(message = "Выберите хотя бы одну площадку")
    @Schema(description = "Площадки, с которых заказчик принимает ролики: INSTAGRAM, TIKTOK, YOUTUBE_SHORTS",
            required = true, example = "[\"TIKTOK\", \"YOUTUBE_SHORTS\"]")
    private Set<Platform> platforms;

    @NotNull(message = "Укажите регион просмотров")
    @Schema(description = "Регион, просмотры из которого оплачиваются: RUSSIA (только РФ), CIS (СНГ), WORLD (весь мир)",
            required = true, example = "RUSSIA")
    private ViewRegion viewRegion;

    @Positive(message = "Минимальная длина ролика должна быть больше нуля")
    @Schema(description = "Минимальная длина ролика в секундах; null — без ограничения", example = "30")
    private Integer minVideoSeconds;

    @Positive(message = "Порог оплачиваемых просмотров должен быть больше нуля")
    @Schema(description = "Сколько просмотров должен набрать ролик, чтобы его оплатили; "
            + "ниже порога начислений нет, null — оплачиваются все просмотры", example = "1000")
    private Long minPaidViews;

    @Positive(message = "Лимит роликов от одного криатора должен быть больше нуля")
    @Schema(description = "Сколько роликов может подать один криатор; null — без ограничения", example = "3")
    private Integer maxVideosPerCreator;

    @Schema(description = "С какого момента объявление принимает отклики, ISO-8601; null — сразу",
            example = "2026-09-20T00:00:00Z")
    private Instant startsAt;

    @Schema(description = "До какого момента объявление принимает отклики, ISO-8601; null — бессрочно",
            example = "2026-10-20T20:59:59.999Z")
    private Instant endsAt;

    @Valid
    @Size(max = 10, message = "Не больше 10 материалов")
    @Schema(description = "Материалы для криатора: файлы и ссылки, в порядке показа; null — без материалов")
    private List<CampaignMaterialRequestDTO> materials;

    /** null — при создании берётся DRAFT, при обновлении статус не трогаем. */
    @Schema(description = "Статус; null — не менять (при создании DRAFT)", example = "ACTIVE")
    private CampaignStatus status;
}
