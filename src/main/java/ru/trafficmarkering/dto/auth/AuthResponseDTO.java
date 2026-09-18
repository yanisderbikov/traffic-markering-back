package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Результат входа")
public record AuthResponseDTO(
        @Schema(description = "JWT для заголовка Authorization: Bearer") String token,
        @Schema(description = "Роль: CUSTOMER, CREATOR, FINANCE_MANAGER, ADMIN или SUPER_ADMIN") String role,
        @Schema(description = "Почта, она же логин") String email,
        String name
) {
}
