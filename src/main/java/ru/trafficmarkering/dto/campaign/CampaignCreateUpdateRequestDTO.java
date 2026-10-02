package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
@Schema(description = "Создание/обновление объявления. Черновик можно сохранять частично: "
        + "пустые поля остаются незаполненными. Запустить объявление можно, только когда заполнены "
        + "заголовок, описание, фотография, тематика, площадки, ставка, бюджет и порог вывода")
public class CampaignCreateUpdateRequestDTO {

    @Size(max = 255, message = "Заголовок не длиннее 255 символов")
    @Schema(description = "Заголовок объявления; обязателен для запуска", example = "Обзор приложения для доставки еды")
    private String title;

    @Schema(description = "Что нужно снять: формат, хронометраж, требования; обязательно для запуска")
    private String description;

    @Size(max = 512, message = "Ключ фотографии не длиннее 512 символов")
    @Schema(description = "Ключ загруженной фотографии из /api/files/campaign-photo/presign; обязателен для запуска")
    private String photoKey;

    @Schema(description = "Код тематики из /api/campaigns/topics; обязательна для запуска", example = "TECH")
    private String topic;

    @Positive(message = "Ставка должна быть больше нуля")
    @Schema(description = "Ставка за 1000 просмотров, в копейках; обязательна для запуска", example = "35000")
    private Long ratePerThousandKopecks;

    @PositiveOrZero(message = "Бюджет не может быть отрицательным")
    @Schema(description = "Выделенный бюджет, в копейках; обязателен для запуска", example = "5000000")
    private Long budgetKopecks;

    @Positive(message = "Порог вывода должен быть больше нуля")
    @Schema(description = "С какой накопленной по объявлению суммы криатор может выводить заработанное, в копейках; "
            + "обязателен для запуска", example = "300000")
    private Long minPayoutKopecks;

    @Schema(description = "Площадки, с которых заказчик принимает ролики: INSTAGRAM, TIKTOK, YOUTUBE_SHORTS; "
            + "для запуска нужна хотя бы одна", example = "[\"TIKTOK\", \"YOUTUBE_SHORTS\"]")
    private Set<Platform> platforms;

    @Schema(description = "Регион, просмотры из которого оплачиваются: CIS (СНГ), WORLD (весь мир); "
            + "null — весь мир", example = "CIS")
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
