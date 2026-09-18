package ru.trafficmarkering.dto.admin;

import ru.trafficmarkering.model.User;

public record AdminUserDTO(
        Long id,
        String email,
        String name,
        String role,
        String roleDescription,
        String verifiedAt,
        String createdAt
) {
    public static AdminUserDTO from(User user) {
        return new AdminUserDTO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getRole() != null ? user.getRole().getDescription() : null,
                user.getVerifiedAt() != null ? user.getVerifiedAt().toString() : null,
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
    }
}
