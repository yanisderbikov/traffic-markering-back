package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CustomerProfile;

import java.util.UUID;

@Schema(description = "Объявление целиком: карточка заказчика и публичная страница")
public record CampaignDTO(
        UUID id,
        @Schema(description = "Короткий номер для публичных ссылок") String publicId,
        String title,
        String description,
        @Schema(description = "Временная ссылка на фотографию; null — фото не загружено") String photoUrl,
        @Schema(description = "Ключ фотографии в хранилище; отправляется обратно при обновлении") String photoKey,
        @Schema(description = "Ставка за 1000 просмотров, в копейках") Long ratePerThousandKopecks,
        @Schema(description = "Выделенный бюджет, в копейках") Long budgetKopecks,
        @Schema(description = "Уже начислено криаторам, в копейках") Long spentKopecks,
        @Schema(description = "Остаток бюджета, в копейках") Long remainingKopecks,
        @Schema(description = "Статус: DRAFT, ACTIVE, PAUSED, COMPLETED") String status,
        @Schema(description = "Человекочитаемый статус", example = "Активно") String statusDescription,
        Long customerId,
        String customerName,
        @Schema(description = "Компания заказчика; null — профиль не заполнен") String customerCompany,
        @Schema(description = "Всего откликов по объявлению") Integer applicationsCount,
        @Schema(description = "Сумма просмотров по одобренным откликам") Long totalViews,
        String createdAt,
        String updatedAt
) {
    /**
     * Агрегаты (счётчик откликов и сумма просмотров) считает сервис запросом к БД —
     * тянуть коллекцию откликов ради двух чисел незачем. Заказчик и его профиль
     * передаются готовыми: связи в сущности ленивые, а профиль вообще в другой таблице.
     * Instant отдаём строками — иначе Jackson сериализует их в epoch-секунды.
     *
     * @param customerProfile профиль заказчика; null — профиль ещё не заполнен
     */
    public static CampaignDTO from(Campaign campaign,
                                   User customer,
                                   CustomerProfile customerProfile,
                                   String photoUrl,
                                   int applicationsCount,
                                   long totalViews) {
        return new CampaignDTO(
                campaign.getId(),
                campaign.getPublicId(),
                campaign.getTitle(),
                campaign.getDescription(),
                photoUrl,
                campaign.getPhotoKey(),
                campaign.getRatePerThousandKopecks(),
                campaign.getBudgetKopecks(),
                campaign.getSpentKopecks(),
                campaign.remainingKopecks(),
                campaign.getStatus() != null ? campaign.getStatus().name() : null,
                campaign.getStatus() != null ? campaign.getStatus().getDescription() : null,
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : null,
                customerProfile != null ? customerProfile.getCompany() : null,
                applicationsCount,
                totalViews,
                campaign.getCreatedAt() != null ? campaign.getCreatedAt().toString() : null,
                campaign.getUpdatedAt() != null ? campaign.getUpdatedAt().toString() : null);
    }

    /** Короткая форма: заказчик берётся из самого объявления (нужна открытая транзакция). */
    public static CampaignDTO from(Campaign campaign,
                                   CustomerProfile customerProfile,
                                   String photoUrl,
                                   int applicationsCount,
                                   long totalViews) {
        return from(campaign, campaign.getCustomer(), customerProfile, photoUrl, applicationsCount, totalViews);
    }
}
