package ru.trafficmarkering.dto.partner;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.partner.ReferralReward;
import ru.trafficmarkering.model.wallet.WalletTransaction;

@Schema(description = "Начисление партнёру с комиссии платформы по операции приглашённого")
public record PartnerRewardDTO(
        @Schema(description = "Публичный номер начисления в кошельке партнёра", example = "K7Q2M9XA") String publicId,
        @Schema(description = "Кто из приглашённых принёс начисление") String referralName,
        @Schema(description = "Операция приглашённого: TOP_UP или WITHDRAWAL") String sourceType,
        @Schema(description = "Человекочитаемая операция", example = "Пополнение") String sourceTypeDescription,
        @Schema(description = "Комиссия платформы с операции, в копейках") long commissionKopecks,
        @Schema(description = "Доля партнёра, в копейках") long rewardKopecks,
        String createdAt
) {
    public static PartnerRewardDTO from(ReferralReward reward) {
        WalletTransaction source = reward.getSourceTransaction();
        return new PartnerRewardDTO(
                reward.getRewardTransaction().getPublicId(),
                reward.getReferral().getName(),
                source.getType().name(),
                source.getType().getDescription(),
                reward.getCommissionKopecks(),
                reward.reward(),
                reward.getCreatedAt() != null ? reward.getCreatedAt().toString() : null);
    }
}
