package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.wallet.Wallet;

import java.util.UUID;

@Schema(description = "Кошелёк текущего пользователя")
public record WalletDTO(
        UUID id,
        Long userId,
        Role role,
        @Schema(description = "Текущий баланс в копейках") long balanceKopecks,
        @Schema(description = "Последнее изменение баланса/кошелька, ISO-8601") String updatedAt
) {
    public static WalletDTO from(Wallet wallet) {
        return new WalletDTO(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getUser().getRole(),
                wallet.getBalanceKopecks(),
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt().toString() : null);
    }
}
