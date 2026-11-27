package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.trafficmarkering.model.campaign.CampaignContentRate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.campaign.PaymentModel;
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

    @Schema(description = "Код основной тематики из /api/campaigns/topics; обязательна для запуска. "
            + "Если тематик несколько, это первая из contentRates", example = "TECH")
    private String topic;

    @Positive(message = "Ставка должна быть больше нуля")
    @Schema(description = "Ставка за 1000 просмотров, в копейках; обязательна для запуска", example = "35000")
    private Long ratePerThousandKopecks;

    @Valid
    @Size(max = CampaignContentRate.MAX_COUNT, message = "Не больше 20 тематик со своими ставками")
    @Schema(description = "Несколько тематик со своими ставками: креатор при подаче выбирает тематику, ролик считается по её ставке. "
            + "Пусто или null — одна ставка ratePerThousandKopecks на все ролики; если задано, ratePerThousandKopecks "
            + "становится наибольшей из ставок")
    private List<CampaignContentRateDTO> contentRates;

    @PositiveOrZero(message = "Бюджет не может быть отрицательным")
    @Schema(description = "Выделенный бюджет, в копейках; обязателен для запуска", example = "5000000")
    private Long budgetKopecks;

    @Positive(message = "Порог вывода должен быть больше нуля")
    @Schema(description = "С какой накопленной по объявлению суммы креатор может выводить заработанное, в копейках; "
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

    @Positive(message = "Лимит роликов от одного креатора должен быть больше нуля")
    @Schema(description = "Сколько роликов может подать один креатор; null — без ограничения", example = "3")
    private Integer maxVideosPerCreator;

    @Schema(description = "С какого момента объявление принимает отклики, ISO-8601; null — сразу",
            example = "2026-09-20T00:00:00Z")
    private Instant startsAt;

    @Schema(description = "До какого момента объявление принимает отклики, ISO-8601; null — бессрочно",
            example = "2026-10-20T20:59:59.999Z")
    private Instant endsAt;

    @Schema(description = "Как платить за просмотры: CONTINUOUS — за просмотры на момент подачи ролика (по умолчанию), "
            + "SNAPSHOT — «ссылка и промокод»: по снимку при подаче, база ставки плюс надбавки", example = "SNAPSHOT")
    private PaymentModel paymentModel;

    @Size(max = 1024, message = "Рекламная ссылка не длиннее 1024 символов")
    @Schema(description = "SNAPSHOT: общая рекламная HTTPS-ссылка, которую креаторы ставят в ссылки профиля; "
            + "обязательна, если есть надбавка за ссылку", example = "https://example.com/app?utm_source=offer")
    private String promoUrl;

    @Size(max = 64, message = "Промокод не длиннее 64 символов")
    @Schema(description = "SNAPSHOT: общий многоразовый промокод для закреплённого комментария; "
            + "обязателен, если есть надбавка за промокод", example = "OFFER20")
    private String promoCode;

    @Size(max = 2000, message = "Описание выгоды не длиннее 2000 символов")
    @Schema(description = "SNAPSHOT: что даёт промокод и на каких условиях — попадёт в шаблон комментария",
            example = "−20% на первый месяц, до 31 декабря")
    private String promoBenefit;

    @PositiveOrZero(message = "Надбавка за ссылку не может быть отрицательной")
    @Schema(description = "SNAPSHOT: надбавка к базовой ставке за ссылку в профиле, в копейках за 1000 просмотров; "
            + "0 или null — не предлагается", example = "30000")
    private Long linkBonusPerThousandKopecks;

    @PositiveOrZero(message = "Надбавка за промокод не может быть отрицательной")
    @Schema(description = "SNAPSHOT: надбавка к базовой ставке за промокод в закрепе, в копейках за 1000 просмотров; "
            + "0 или null — не предлагается", example = "20000")
    private Long promoBonusPerThousandKopecks;

    @Min(value = 1, message = "Срок размещения — от 1 дня")
    @Max(value = 90, message = "Срок размещения — не больше 90 дней")
    @Schema(description = "SNAPSHOT: сколько дней после подачи держать ссылку и закреп; null — 7", example = "7")
    private Integer placementRetentionDays;

    @Positive(message = "Потолок за ролик должен быть больше нуля")
    @Schema(description = "SNAPSHOT: потолок вознаграждения за один ролик, в копейках; null — без потолка",
            example = "1000000")
    private Long maxRewardPerVideoKopecks;

    @Valid
    @Size(max = 10, message = "Не больше 10 материалов")
    @Schema(description = "Материалы для креатора: файлы и ссылки, в порядке показа; null — без материалов")
    private List<CampaignMaterialRequestDTO> materials;

    /** null — при создании берётся DRAFT, при обновлении статус не трогаем. */
    @Schema(description = "Статус; null — не менять (при создании DRAFT)", example = "ACTIVE")
    private CampaignStatus status;
}
