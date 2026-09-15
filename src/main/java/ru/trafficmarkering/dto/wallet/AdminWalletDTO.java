package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.wallet.Wallet;

import java.util.UUID;

@Schema(description = "Кошелёк в админском списке")
public record AdminWalletDTO(
        Long userId,
        String username,
        String name,
        Role role,
        UUID walletId,
        long balanceKopecks,
        String updatedAt
) {
    public static AdminWalletDTO from(Wallet wallet) {
        return new AdminWalletDTO(
                wallet.getUser().getId(),
                wallet.getUser().getUsername(),
                wallet.getUser().getName(),
                wallet.getUser().getRole(),
                wallet.getId(),
                wallet.getBalanceKopecks(),
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt().toString() : null);
    }
}
