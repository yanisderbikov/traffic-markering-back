package ru.trafficmarkering.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CustomerProfile;

import java.util.UUID;

@Schema(description = "Профиль заказчика")
public record CustomerProfileDTO(
        UUID id,
        @Schema(description = "ID пользователя-заказчика") Long userId,
        @Schema(description = "Имя из учётной записи") String name,
        String company,
        String about,
        String telegram,
        String website,
        @Schema(description = "Когда профиль правили в последний раз, ISO-8601") String updatedAt
) {
    /**
     * Instant отдаём строкой: без явной настройки Jackson сериализует его в epoch-секунды.
     * Пользователя передаём отдельно — связь профиля с ним ленивая, а имя нужно всегда.
     */
    public static CustomerProfileDTO from(CustomerProfile profile, User user) {
        return new CustomerProfileDTO(
                profile.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getName() : null,
                profile.getCompany(),
                profile.getAbout(),
                profile.getTelegram(),
                profile.getWebsite(),
                profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : null);
    }

    /** Короткая форма: пользователь берётся из самого профиля (нужна открытая транзакция). */
    public static CustomerProfileDTO from(CustomerProfile profile) {
        return from(profile, profile.getUser());
    }
}
