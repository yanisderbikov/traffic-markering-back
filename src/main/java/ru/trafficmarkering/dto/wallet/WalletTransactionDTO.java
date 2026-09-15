package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

import java.util.UUID;

@Schema(description = "Операция кошелька")
public record WalletTransactionDTO(
        UUID id,
        WalletTransactionType type,
        @Schema(description = "Изменение в копейках: плюс — пополнение, минус — списание") long amountKopecks,
        @Schema(description = "Баланс сразу после операции в копейках") long balanceAfterKopecks,
        String reason,
        String actorName,
        String createdAt
) {
    public static WalletTransactionDTO from(WalletTransaction transaction) {
        return new WalletTransactionDTO(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmountKopecks(),
                transaction.getBalanceAfterKopecks(),
                transaction.getReason(),
                transaction.getActor() != null ? transaction.getActor().getName() : null,
                transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : null);
    }
}
