package ru.trafficmarkering.dto;

import ru.trafficmarkering.service.auth.telegram.TelegramIdentity;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.CabinetTab;
import ru.trafficmarkering.model.User;

import java.util.Collection;
import java.util.List;

@Schema(description = "Текущий пользователь")
public record CurrentUserDTO(
        Long id,
        @Schema(description = "Логин (e-mail)") String username,
        String name,
        @Schema(description = "Роль: CUSTOMER, CREATOR, FINANCE_MANAGER, ADMIN или SUPER_ADMIN") String role,
        @Schema(description = "Вкладки кабинета в порядке меню") List<CabinetTabDTO> tabs,
        @Schema(description = "Временная ссылка на аватарку; null — аватарки нет") String avatarUrl,
        @Schema(description = "Почта для входа; null — учётка заведена через Telegram и почты пока нет") String email,
        @Schema(description = "true — войти можно только через Telegram") boolean telegramOnly
) {
    public static CurrentUserDTO from(User user, Collection<CabinetTab> tabs, String avatarUrl) {
        return new CurrentUserDTO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole() != null ? user.getRole().name() : null,
                tabs.stream().map(CabinetTabDTO::from).toList(),
                avatarUrl,
                TelegramIdentity.telegramOnly(user) ? null : user.getUsername(),
                TelegramIdentity.telegramOnly(user));
    }
}
