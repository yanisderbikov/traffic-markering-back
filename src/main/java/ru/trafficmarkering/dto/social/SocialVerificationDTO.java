package ru.trafficmarkering.dto.social;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Найденный профиль и код, который криатор добавляет в его описание")
public record SocialVerificationDTO(
        @Schema(description = "Площадка", example = "TIKTOK") String platform,
        @Schema(description = "Название площадки для интерфейса", example = "TikTok") String platformLabel,
        @Schema(description = "Идентификатор аккаунта на стороне площадки") String externalId,
        @Schema(description = "Ник; у YouTube может быть null") String username,
        String displayName,
        String avatarUrl,
        Long followers,
        @Schema(description = "Код для описания профиля", example = "OFFER-7KQ2PX") String code,
        @Schema(description = "Куда вставить код, для подсказки в интерфейсе") String codePlacement,
        @Schema(description = "Код уже стоит в описании — можно сразу подтверждать") boolean codeFound,
        @Schema(description = "Аккаунт уже привязан к этому криатору") boolean alreadyConnected
) {
}
