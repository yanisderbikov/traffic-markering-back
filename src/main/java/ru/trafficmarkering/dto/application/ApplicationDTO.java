package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CreatorProfile;

import java.util.UUID;

@Schema(description = "Отклик криатора: и в списке заказчика, и в списке криатора")
public record ApplicationDTO(
        UUID id,
        @Schema(description = "Короткий номер отклика") String publicId,
        UUID campaignId,
        String campaignTitle,
        @Schema(description = "Ставка объявления за 1000 просмотров, в копейках") Long ratePerThousandKopecks,
        Long creatorId,
        String creatorName,
        @Schema(description = "Telegram криатора из профиля; null — не заполнен") String creatorTelegram,
        @Schema(description = "Площадка: TELEGRAM, INSTAGRAM, TIKTOK, YOUTUBE_SHORTS") String platform,
        @Schema(description = "Человекочитаемая площадка", example = "TikTok") String platformDescription,
        String videoUrl,
        String comment,
        @Schema(description = "Статус: PENDING, APPROVED, REJECTED, COMPLETED") String status,
        @Schema(description = "Человекочитаемый статус", example = "Одобрен") String statusDescription,
        @Schema(description = "Набранные просмотры") Long views,
        @Schema(description = "Начислено криатору, в копейках") Long accruedKopecks,
        @Schema(description = "Когда просмотры обновлялись в последний раз, ISO-8601") String viewsSyncedAt,
        String createdAt,
        String updatedAt
) {
    /**
     * Объявление и криатора передаём готовыми: обе связи в сущности ленивые, а имя
     * криатора и заголовок объявления нужны в каждом списке. Telegram лежит в профиле —
     * это отдельная таблица, поэтому он тоже приходит извне.
     * Instant отдаём строками — иначе Jackson сериализует их в epoch-секунды.
     *
     * @param creatorProfile профиль криатора; null — профиль ещё не заполнен
     */
    public static ApplicationDTO from(Application application,
                                      Campaign campaign,
                                      User creator,
                                      CreatorProfile creatorProfile) {
        return new ApplicationDTO(
                application.getId(),
                application.getPublicId(),
                campaign != null ? campaign.getId() : null,
                campaign != null ? campaign.getTitle() : null,
                campaign != null ? campaign.getRatePerThousandKopecks() : null,
                creator != null ? creator.getId() : null,
                creator != null ? creator.getName() : null,
                creatorProfile != null ? creatorProfile.getTelegram() : null,
                application.getPlatform() != null ? application.getPlatform().name() : null,
                application.getPlatform() != null ? application.getPlatform().getDescription() : null,
                application.getVideoUrl(),
                application.getComment(),
                application.getStatus() != null ? application.getStatus().name() : null,
                application.getStatus() != null ? application.getStatus().getDescription() : null,
                application.getViews(),
                application.getAccruedKopecks(),
                application.getViewsSyncedAt() != null ? application.getViewsSyncedAt().toString() : null,
                application.getCreatedAt() != null ? application.getCreatedAt().toString() : null,
                application.getUpdatedAt() != null ? application.getUpdatedAt().toString() : null);
    }

    /** Короткая форма: объявление и криатор берутся из отклика (нужна открытая транзакция). */
    public static ApplicationDTO from(Application application, CreatorProfile creatorProfile) {
        return from(application, application.getCampaign(), application.getCreator(), creatorProfile);
    }
}
