package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CustomerProfile;

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
                customer != null ? customer.getName() : null,
                customerProfile != null ? customerProfile.getCompany() : null,
                applicationsCount,
                campaign.getCreatedAt() != null ? campaign.getCreatedAt().toString() : null);
    }
}
