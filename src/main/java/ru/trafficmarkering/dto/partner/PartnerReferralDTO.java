package ru.trafficmarkering.dto.partner;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Рекламодатель, зарегистрированный по приглашению партнёра")
public record PartnerReferralDTO(
        @Schema(description = "Имя рекламодателя") String name,
        @Schema(description = "Когда зарегистрировался, ISO-8601") String joinedAt,
        @Schema(description = "Сколько партнёр заработал на нём, в копейках") long earnedKopecks,
        @Schema(description = "Сколько начислений он принёс") int rewardsCount
) {
}
