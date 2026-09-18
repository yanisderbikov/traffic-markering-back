package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CustomerProfile;

import java.util.List;
import java.util.UUID;

@Schema(description = "Карточка объявления на публичной доске")
public record CampaignBoardDTO(
        UUID id,
        @Schema(description = "Короткий номер для публичных ссылок") String publicId,
        String title,
        @Schema(description = "Временная ссылка на фотографию; null — фото не загружено") String photoUrl,
        @Schema(description = "Ставка за 1000 просмотров, в копейках") Long ratePerThousandKopecks,
        @Schema(description = "Выделенный бюджет, в копейках") Long budgetKopecks,
        @Schema(description = "Уже начислено криаторам, в копейках") Long spentKopecks,
        @Schema(description = "Остаток бюджета, в копейках") Long remainingKopecks,
        @Schema(description = "С какой накопленной по объявлению суммы криатор может выводить заработанное, в копейках") Long minPayoutKopecks,
        @Schema(description = "Площадки, с которых принимаются ролики: INSTAGRAM, TIKTOK, YOUTUBE_SHORTS") List<String> platforms,
        @Schema(description = "Регион, просмотры из которого оплачиваются: RUSSIA, CIS, WORLD") String viewRegion,
        @Schema(description = "Человекочитаемый регион просмотров", example = "Только РФ") String viewRegionDescription,
        @Schema(description = "Минимальная длина ролика в секундах; null — без ограничения") Integer minVideoSeconds,
        @Schema(description = "Сколько просмотров должен набрать ролик, чтобы его оплатили; null — оплачиваются все") Long minPaidViews,
        @Schema(description = "Сколько роликов может подать один криатор; null — без ограничения") Integer maxVideosPerCreator,
        @Schema(description = "С какого момента принимаются отклики, ISO-8601; null — сразу") String startsAt,
        @Schema(description = "До какого момента принимаются отклики, ISO-8601; null — бессрочно") String endsAt,
        @Schema(description = "Сколько материалов приложил заказчик") int materialsCount,
        String customerName,
        @Schema(description = "Компания заказчика; null — профиль не заполнен") String customerCompany,
        @Schema(description = "Всего откликов по объявлению") Integer applicationsCount,
        String createdAt
) {
    /**
     * Счётчик откликов сервис считает запросом к БД, заказчик и его профиль передаются
     * готовыми: связь объявления с пользователем ленивая, профиль лежит в другой таблице.
     *
     * @param customerProfile профиль заказчика; null — профиль ещё не заполнен
     */
    public static CampaignBoardDTO from(Campaign campaign,
                                        User customer,
                                        CustomerProfile customerProfile,
                                        String photoUrl,
                                        int applicationsCount) {
        return new CampaignBoardDTO(
                campaign.getId(),
                campaign.getPublicId(),
                campaign.getTitle(),
                photoUrl,
                campaign.getRatePerThousandKopecks(),
                campaign.getBudgetKopecks(),
                campaign.getSpentKopecks(),
                campaign.remainingKopecks(),
                campaign.getMinPayoutKopecks(),
                campaign.getPlatforms().stream().sorted().map(Platform::name).toList(),
                campaign.viewRegion().name(),
                campaign.viewRegion().getDescription(),
                campaign.getMinVideoSeconds(),
                campaign.getMinPaidViews(),
                campaign.getMaxVideosPerCreator(),
                campaign.getStartsAt() != null ? campaign.getStartsAt().toString() : null,
                campaign.getEndsAt() != null ? campaign.getEndsAt().toString() : null,
                campaign.getMaterials().size(),
                customer != null ? customer.getName() : null,
                customerProfile != null ? customerProfile.getCompany() : null,
                applicationsCount,
                campaign.getCreatedAt() != null ? campaign.getCreatedAt().toString() : null);
    }
}
