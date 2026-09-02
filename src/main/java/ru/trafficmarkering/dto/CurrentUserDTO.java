package ru.trafficmarkering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;

@Schema(description = "Текущий пользователь")
public record CurrentUserDTO(
        Long id,
        @Schema(description = "Логин (e-mail)") String username,
        String name,
        @Schema(description = "Роль: CUSTOMER, CREATOR или ADMIN") String role
) {
    public static CurrentUserDTO from(User user) {
        return new CurrentUserDTO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole() != null ? user.getRole().name() : null);
    }
}
