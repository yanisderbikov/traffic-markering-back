package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

@Schema(description = "Строка списка операций: суть, откуда → куда и статус; подробности — отдельной ручкой по id")
public record OperationRowDTO(
        Long id,
        @Schema(description = "Тип: TOP_UP, WITHDRAWAL, ALLOCATION, RELEASE, EARNING, PAYOUT") String type,
        @Schema(description = "Что за операция", example = "Начисление за просмотры") String title,
        @Schema(description = "Уточнение: объявление или комментарий; может быть null") String subtitle,
        @Schema(description = "Сумма в копейках со знаком относительно кошелька") Long amountKopecks,
        @Schema(description = "Статус: DONE, PENDING, SENT, CONFIRMED, REJECTED, CANCELLED") String status,
        String statusDescription,
        @Schema(description = "Чей кошелёк") String ownerName,
        @Schema(description = "Откуда ушли деньги") FlowPointDTO source,
        @Schema(description = "Куда пришли деньги") FlowPointDTO destination,
        String createdAt
) {
    public static OperationRowDTO from(WalletTransaction transaction, Transfer transfer) {
        var campaign = transaction.getCampaign();
        User owner = transaction.getWallet() != null ? transaction.getWallet().getUser() : null;
        MoneyFlowDTO flow = MoneyFlowDTO.of(transaction, transfer);
        String subtitle = campaign != null ? campaign.getTitle()
                : transaction.getType() == WalletTransactionType.PAYOUT ? null : transaction.getComment();
        return new OperationRowDTO(
                transaction.getId(),
                transaction.getType() != null ? transaction.getType().name() : null,
                transaction.getType() != null ? transaction.getType().getDescription() : null,
                subtitle,
                transaction.getAmountKopecks(),
                transaction.getStatus() != null ? transaction.getStatus().name() : null,
                transaction.getStatus() != null ? transaction.getStatus().getDescription() : null,
                owner != null ? owner.getName() : null,
                flow.source(),
                flow.destination(),
                transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : null);
    }
}
