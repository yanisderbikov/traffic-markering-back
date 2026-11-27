package ru.trafficmarkering.dto.placement;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Предварительный расчёт работы «ссылка и промокод» по свежему снимку просмотров. "
        + "Подтверждается подачей с тем же snapshotId, пока не истёк expiresAt")
public record SnapshotQuoteDTO(
        @Schema(description = "Номер снимка: его подтверждает подача") Long snapshotId,
        @Schema(description = "Когда сняты просмотры, ISO-8601") String capturedAt,
        @Schema(description = "До какого момента расчёт можно подтвердить, ISO-8601") String expiresAt,
        @Schema(description = "Откуда просмотры", example = "ScrapeCreators") String source,
        @Schema(description = "Ссылка на ролик, развёрнутая до полной") String videoUrl,
        @Schema(description = "Площадка ролика", example = "TikTok") String platformDescription,
        @Schema(description = "Когда ролик опубликован, ISO-8601; null — площадка не отдала") String videoPublishedAt,

        @Schema(description = "Все просмотры ролика на момент снимка") Long views,
        @Schema(description = "Просмотры из региона объявления") Long payableViews,
        @Schema(description = "Известна ли география (для «весь мир» всегда true)") boolean geographyKnown,
        @Schema(description = "Оплачиваемые просмотры: после потолка новичка, округлены вниз до сотен") Long billableViews,
        @Schema(description = "Просмотры урезаны потолком для новых креаторов") boolean cappedForNewCreator,
        @Schema(description = "Порог оплаты объявления; null — без порога") Long minPaidViews,
        @Schema(description = "Ролик не дотянул до порога оплаты: сумма 0") boolean belowMinPaidViews,

        @Schema(description = "Ставка за 1000 просмотров, в копейках: база") Long baseRatePerThousandKopecks,
        @Schema(description = "Надбавка за ссылку, в копейках за 1000 просмотров") Long linkBonusPerThousandKopecks,
        @Schema(description = "Надбавка за промокод, в копейках за 1000 просмотров") Long promoBonusPerThousandKopecks,
        @Schema(description = "Ставка выбранного варианта") Long rateKopecks,
        @Schema(description = "Выбрана надбавка за ссылку") boolean withLink,
        @Schema(description = "Выбрана надбавка за промокод") boolean withPromo,

        @Schema(description = "База, в копейках") Long baseKopecks,
        @Schema(description = "Надбавка за ссылку, в копейках; 0 — не выбрана") Long linkKopecks,
        @Schema(description = "Надбавка за промокод, в копейках; 0 — не выбрана") Long promoKopecks,
        @Schema(description = "База и надбавки до потолка на ролик и остатка бюджета") Long grossKopecks,
        @Schema(description = "Потолок вознаграждения за ролик; null — без потолка") Long maxRewardPerVideoKopecks,
        @Schema(description = "Остаток бюджета объявления") Long budgetRemainingKopecks,
        @Schema(description = "Сумма упёрлась в потолок на ролик или остаток бюджета") boolean limited,
        @Schema(description = "Сумма работы при подтверждении всех выбранных надбавок") Long totalKopecks,
        @Schema(description = "Сумма, если ни одну надбавку не подтвердят: только база") Long baseOnlyKopecks,
        @Schema(description = "Комиссия платформы при выводе, %") BigDecimal commissionPercent,
        @Schema(description = "Комиссия с totalKopecks") Long commissionKopecks,
        @Schema(description = "totalKopecks после комиссии") Long netKopecks,

        @Schema(description = "Автопроверка ссылки в профиле: FOUND, MISSING, UNKNOWN; null — ссылка не выбрана") String linkCheckState,
        @Schema(description = "Человекочитаемый итог автопроверки") String linkCheckStateDescription,
        @Schema(description = "Что увидела автопроверка") String linkCheckDetail,
        @Schema(description = "Сколько дней держать ссылку и закреп после подачи") Integer retentionDays,
        @Schema(description = "До какого момента держать ссылку и закреп, если подать сейчас, ISO-8601") String retainUntil
) {
}
