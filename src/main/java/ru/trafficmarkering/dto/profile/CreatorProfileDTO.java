package ru.trafficmarkering.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CreatorProfile;

import java.util.UUID;

@Schema(description = "Профиль криатора")
public record CreatorProfileDTO(
        UUID id,
        @Schema(description = "ID пользователя-криатора") Long userId,
        @Schema(description = "Имя из учётной записи") String name,
        @Schema(description = "Отображаемое имя; может быть null") String displayName,
        String bio,
        String telegram,
        String instagram,
        String tiktok,
        String youtubeShorts,
        @Schema(description = "Когда профиль правили в последний раз, ISO-8601") String updatedAt
) {
    /**
     * Instant отдаём строкой: без явной настройки Jackson сериализует его в epoch-секунды.
     * Пользователя передаём отдельно — связь профиля с ним ленивая, а имя нужно всегда.
     */
    public static CreatorProfileDTO from(CreatorProfile profile, User user) {
        return new CreatorProfileDTO(
                profile.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getName() : null,
                profile.getDisplayName(),
                profile.getBio(),
                profile.getTelegram(),
                profile.getInstagram(),
                profile.getTiktok(),
                profile.getYoutubeShorts(),
                profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : null);
    }

    /** Короткая форма: пользователь берётся из самого профиля (нужна открытая транзакция). */
    public static CreatorProfileDTO from(CreatorProfile profile) {
        return from(profile, profile.getUser());
    }
}
