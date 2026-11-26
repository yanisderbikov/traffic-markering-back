package ru.trafficmarkering.dto.partner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Партнёрская программа рекламодателя: условия, код приглашения и что принесли приглашённые")
public record PartnerDTO(
        @Schema(description = "Подключена ли программа") boolean active,
        @Schema(description = "Код приглашения для ссылки на лендинг; null — программа не подключена", example = "K7Q2M9XA") String code,
        @Schema(description = "Когда подключена, ISO-8601; null — не подключена") String joinedAt,
        @Schema(description = "Комиссия платформы с пополнений и выводов, %", example = "10") BigDecimal commissionPercent,
        @Schema(description = "Доля партнёра от комиссии платформы с приглашённых, %", example = "10") BigDecimal partnerSharePercent,
        @Schema(description = "Сколько рекламодателей зарегистрировалось по приглашению") int invitedCount,
        @Schema(description = "Сколько из них уже принесли вознаграждение") int activeCount,
        @Schema(description = "Заработано за всё время, в копейках") long earnedKopecks,
        @Schema(description = "Приглашённые рекламодатели, новые сверху") List<PartnerReferralDTO> referrals,
        @Schema(description = "Последние начисления, новые сверху") List<PartnerRewardDTO> rewards
) {
}
