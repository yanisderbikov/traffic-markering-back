package ru.trafficmarkering.dto.placement;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.PlacementKind;
import ru.trafficmarkering.model.application.RateParts;
import ru.trafficmarkering.model.campaign.Campaign;

@Schema(description = "Условия и состояние работы «ссылка и промокод»: ставки, снимок при подаче, материалы, надбавки")
public record ApplicationPlacementDTO(
        @Schema(description = "Базовая ставка за 1000 просмотров на момент взятия оффера, в копейках") Long baseRatePerThousandKopecks,
        @Schema(description = "Надбавка за ссылку в профиле, в копейках за 1000 просмотров; 0 — не предлагается") Long linkBonusPerThousandKopecks,
        @Schema(description = "Надбавка за промокод в закрепе, в копейках за 1000 просмотров; 0 — не предлагается") Long promoBonusPerThousandKopecks,
        @Schema(description = "Ставка выбранного варианта: база и выбранные надбавки") Long selectedRatePerThousandKopecks,
        @Schema(description = "Ставка, под которую занят бюджет: база и надбавки без отказа") Long reservedRatePerThousandKopecks,
        @Schema(description = "Ставка, которая уже уходит в кошелёк: база и засчитанные надбавки") Long creditableRatePerThousandKopecks,
        @Schema(description = "Оплачиваемые просмотры из снимка при подаче; null — работа ещё не подана") Long billingViews,
        @Schema(description = "Когда снят снимок при подаче, ISO-8601") String billingCapturedAt,
        @Schema(description = "Откуда снимок", example = "ScrapeCreators") String billingSource,
        @Schema(description = "Когда работа подана, ISO-8601; null — ещё не подана") String submittedAt,
        @Schema(description = "До какого момента держать ссылку и закреп, ISO-8601; null — работа ещё не подана") String retainUntil,
        @Schema(description = "Сколько дней после подачи держать ссылку и закреп") Integer retentionDays,
        @Schema(description = "Потолок вознаграждения за ролик, в копейках; null — без потолка") Long maxRewardPerVideoKopecks,
        @Schema(description = "Общая рекламная ссылка кампании") String promoUrl,
        @Schema(description = "Общий промокод кампании") String promoCode,
        @Schema(description = "Что даёт промокод зрителю") String promoBenefit,
        PlacementDTO link,
        PlacementDTO promo
) {
    public static ApplicationPlacementDTO from(Application application, Campaign campaign,
                                               String linkProofFileUrl, String promoProofFileUrl) {
        RateParts rates = application.rateParts();
        ApplicationViewSnapshot billing = application.getBillingSnapshot();
        boolean offersLink = rates.link() > 0 && campaign.getPromoUrl() != null;
        boolean offersPromo = rates.promo() > 0 && campaign.getPromoCode() != null;
        return new ApplicationPlacementDTO(
                rates.base(),
                rates.link(),
                rates.promo(),
                rates.rate(application.link().state().selected(), application.promo().state().selected()),
                application.reservedRate(),
                application.creditableRate(),
                application.getBillingViews(),
                billing != null && billing.getCapturedAt() != null ? billing.getCapturedAt().toString() : null,
                billing != null && billing.getSource() != null ? billing.getSource().getDescription() : null,
                application.getSubmittedAt() != null ? application.getSubmittedAt().toString() : null,
                application.getPlacementRetainUntil() != null ? application.getPlacementRetainUntil().toString() : null,
                campaign.retentionDays(),
                campaign.getMaxRewardPerVideoKopecks(),
                campaign.getPromoUrl(),
                campaign.getPromoCode(),
                campaign.getPromoBenefit(),
                PlacementDTO.from(PlacementKind.LINK, offersLink, application.link(), linkProofFileUrl),
                PlacementDTO.from(PlacementKind.PROMO, offersPromo, application.promo(), promoProofFileUrl));
    }
}
