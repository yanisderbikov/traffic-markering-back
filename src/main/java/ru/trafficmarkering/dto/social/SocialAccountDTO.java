package ru.trafficmarkering.dto.social;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.social.SocialAccount;

import java.util.UUID;

@Schema(description = "Привязанный аккаунт соцсети")
public record SocialAccountDTO(
        UUID id,
        @Schema(description = "Площадка", example = "TIKTOK") String platform,
        @Schema(description = "Название площадки для интерфейса", example = "TikTok") String platformLabel,
        @Schema(description = "Идентификатор аккаунта на стороне площадки") String externalId,
        @Schema(description = "Ник аккаунта; может быть null") String username,
        String displayName,
        String avatarUrl,
        Long followers,
        @Schema(description = "ACTIVE — токен живой, EXPIRED — нужна повторная привязка") String status,
        @Schema(description = "Когда аккаунт привязали, ISO-8601") String connectedAt,
        @Schema(description = "До какого момента живёт токен, ISO-8601") String tokenExpiresAt
) {
    public static SocialAccountDTO from(SocialAccount account) {
        return new SocialAccountDTO(
                account.getId(),
                account.getPlatform() != null ? account.getPlatform().name() : null,
                account.getPlatform() != null ? account.getPlatform().getDescription() : null,
                account.getExternalId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getAvatarUrl(),
                account.getFollowers(),
                account.getStatus() != null ? account.getStatus().name() : null,
                account.getConnectedAt() != null ? account.getConnectedAt().toString() : null,
                account.getTokenExpiresAt() != null ? account.getTokenExpiresAt().toString() : null);
    }
}
