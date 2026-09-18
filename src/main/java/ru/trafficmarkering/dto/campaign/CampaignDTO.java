package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CustomerProfile;

import java.time.Instant;
import java.util.List;
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
        @Schema(description = "С какой накопленной по объявлению суммы криатор может выводить заработанное, в копейках") Long minPayoutKopecks,
        @Schema(description = "Статус: DRAFT, ACTIVE, PAUSED, COMPLETED") String status,
        @Schema(description = "Человекочитаемый статус", example = "Активно") String statusDescription,
        @Schema(description = "Площадки, с которых принимаются ролики: INSTAGRAM, TIKTOK, YOUTUBE_SHORTS") List<String> platforms,
        @Schema(description = "Регион, просмотры из которого оплачиваются: RUSSIA, CIS, WORLD") String viewRegion,
        @Schema(description = "Человекочитаемый регион просмотров", example = "Только РФ") String viewRegionDescription,
        @Schema(description = "Минимальная длина ролика в секундах; null — без ограничения") Integer minVideoSeconds,
        @Schema(description = "Сколько просмотров должен набрать ролик, чтобы его оплатили; null — оплачиваются все") Long minPaidViews,
        @Schema(description = "Сколько роликов может подать один криатор; null — без ограничения") Integer maxVideosPerCreator,
        @Schema(description = "С какого момента принимаются отклики, ISO-8601; null — сразу") String startsAt,
        @Schema(description = "До какого момента принимаются отклики, ISO-8601; null — бессрочно") String endsAt,
        @Schema(description = "Принимает ли объявление отклики прямо сейчас: статус ACTIVE и период действия не истёк") boolean acceptingApplications,
        @Schema(description = "Материалы для криатора в порядке показа") List<CampaignMaterialDTO> materials,
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
                                   List<CampaignMaterialDTO> materials,
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
                campaign.getMinPayoutKopecks(),
                campaign.getStatus() != null ? campaign.getStatus().name() : null,
                campaign.getStatus() != null ? campaign.getStatus().getDescription() : null,
                campaign.getPlatforms().stream().sorted().map(Platform::name).toList(),
                campaign.viewRegion().name(),
                campaign.viewRegion().getDescription(),
                campaign.getMinVideoSeconds(),
                campaign.getMinPaidViews(),
                campaign.getMaxVideosPerCreator(),
                campaign.getStartsAt() != null ? campaign.getStartsAt().toString() : null,
                campaign.getEndsAt() != null ? campaign.getEndsAt().toString() : null,
                campaign.acceptsApplicationsAt(Instant.now()),
                materials,
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
                                   List<CampaignMaterialDTO> materials,
                                   int applicationsCount,
                                   long totalViews) {
        return from(campaign, campaign.getCustomer(), customerProfile, photoUrl, materials, applicationsCount, totalViews);
    }
}
