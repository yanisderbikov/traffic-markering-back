package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.dto.fraud.FraudFlagDTO;
import ru.trafficmarkering.model.fraud.TrustLevel;

import java.util.List;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.PayableViews;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.ViewRegion;
import ru.trafficmarkering.model.profile.CreatorProfile;

import java.util.UUID;

@Schema(description = "Отклик криатора: и в списке заказчика, и в списке криатора")
public record ApplicationDTO(
        UUID id,
        @Schema(description = "Короткий номер отклика") String publicId,
        UUID campaignId,
        @Schema(description = "Короткий номер объявления для ссылки на него") String campaignPublicId,
        String campaignTitle,
        @Schema(description = "Когда объявление перестаёт принимать отклики и ролики, ISO-8601; null — бессрочно") String campaignEndsAt,
        @Schema(description = "Ставка объявления за 1000 просмотров, в копейках") Long ratePerThousandKopecks,
        @Schema(description = "Порог вывода объявления: с какой накопленной по нему суммы криатор может выводить, в копейках") Long minPayoutKopecks,
        @Schema(description = "Регион просмотров объявления: CIS, WORLD") String campaignViewRegion,
        @Schema(description = "Человекочитаемый регион", example = "СНГ") String campaignViewRegionDescription,
        Long creatorId,
        String creatorName,
        @Schema(description = "Telegram криатора из профиля; null — не заполнен") String creatorTelegram,
        @Schema(description = "Площадка: TELEGRAM, INSTAGRAM, TIKTOK, YOUTUBE_SHORTS") String platform,
        @Schema(description = "Человекочитаемая площадка", example = "TikTok") String platformDescription,
        String videoUrl,
        String comment,
        @Schema(description = "Статус: IN_PROGRESS (взят в работу, ролика ещё нет), PENDING, APPROVED, REJECTED, COMPLETED") String status,
        @Schema(description = "Человекочитаемый статус", example = "Одобрен") String statusDescription,
        @Schema(description = "Набранные просмотры") Long views,
        @Schema(description = "Просмотры, которые идут в расчёт выплаты: для «весь мир» — все, иначе только из региона; 0, если география неизвестна") Long payableViews,
        @Schema(description = "Известна ли география просмотров (для «весь мир» всегда true)") Boolean viewsGeographyKnown,
        @Schema(description = "Начислено криатору, в копейках") Long accruedKopecks,
        @Schema(description = "Когда просмотры обновлялись в последний раз, ISO-8601") String viewsSyncedAt,
        @Schema(description = "Уже зачислено в кошелёк криатора, в копейках") Long creditedKopecks,
        @Schema(description = "Когда ролик опубликован на площадке, ISO-8601; null — неизвестно") String videoPublishedAt,
        @Schema(description = "Вердикт антифрода: CLEAN, SUSPICIOUS, FRAUD, VERIFIED") String fraudStatus,
        @Schema(description = "Человекочитаемый вердикт", example = "Чисто") String fraudStatusDescription,
        @Schema(description = "Баллы скоринга 0–100; криатору не показываются") Integer fraudScore,
        @Schema(description = "Сработавшие правила; криатору не показываются") List<FraudFlagDTO> fraudFlags,
        @Schema(description = "Репутация криатора: NEW, TRUSTED, RESTRICTED, BLOCKED") String creatorTrustLevel,
        @Schema(description = "Человекочитаемая репутация", example = "Новичок") String creatorTrustLevelDescription,
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
        ViewRegion viewRegion = campaign != null ? campaign.viewRegion() : ViewRegion.WORLD;
        PayableViews payableViews = application.payableViews(viewRegion);
        TrustLevel trustLevel = creatorProfile != null ? creatorProfile.trustLevel() : TrustLevel.NEW;
        return new ApplicationDTO(
                application.getId(),
                application.getPublicId(),
                campaign != null ? campaign.getId() : null,
                campaign != null ? campaign.getPublicId() : null,
                campaign != null ? campaign.getTitle() : null,
                campaign != null && campaign.getEndsAt() != null ? campaign.getEndsAt().toString() : null,
                campaign != null ? campaign.getRatePerThousandKopecks() : null,
                campaign != null ? campaign.getMinPayoutKopecks() : null,
                campaign != null ? viewRegion.name() : null,
                campaign != null ? viewRegion.getDescription() : null,
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
                payableViews.views(),
                payableViews.geographyKnown(),
                application.getAccruedKopecks(),
                application.getViewsSyncedAt() != null ? application.getViewsSyncedAt().toString() : null,
                application.getCreditedKopecks(),
                application.getVideoPublishedAt() != null ? application.getVideoPublishedAt().toString() : null,
                application.fraudStatus().name(),
                application.fraudStatus().getDescription(),
                application.fraudScoreValue(),
                application.fraudFlags().stream().map(FraudFlagDTO::from).toList(),
                trustLevel.name(),
                trustLevel.getDescription(),
                application.getCreatedAt() != null ? application.getCreatedAt().toString() : null,
                application.getUpdatedAt() != null ? application.getUpdatedAt().toString() : null);
    }

    /** Копия для кабинета криатора: вердикт остаётся, баллы и правила скрываются. */
    public ApplicationDTO forCreator() {
        return new ApplicationDTO(id, publicId, campaignId, campaignPublicId, campaignTitle, campaignEndsAt,
                ratePerThousandKopecks, minPayoutKopecks,
                campaignViewRegion, campaignViewRegionDescription, creatorId, creatorName, creatorTelegram, platform,
                platformDescription, videoUrl, comment, status, statusDescription, views, payableViews,
                viewsGeographyKnown, accruedKopecks, viewsSyncedAt, creditedKopecks, videoPublishedAt, fraudStatus,
                fraudStatusDescription, null, List.of(), creatorTrustLevel, creatorTrustLevelDescription,
                createdAt, updatedAt);
    }

    /** Короткая форма: объявление и криатор берутся из отклика (нужна открытая транзакция). */
    public static ApplicationDTO from(Application application, CreatorProfile creatorProfile) {
        return from(application, application.getCampaign(), application.getCreator(), creatorProfile);
    }
}
