package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;

import java.util.UUID;

@Schema(description = "Операция по кошельку; сумма со знаком относительно свободных средств")
public record WalletTransactionDTO(
        Long id,
        @Schema(description = "Тип: TOP_UP, WITHDRAWAL, ALLOCATION, RELEASE, EARNING, PAYOUT") String type,
        @Schema(description = "Человекочитаемый тип", example = "Пополнение") String typeDescription,
        @Schema(description = "Сумма в копейках: плюс — деньги пришли в кошелёк, минус — ушли") Long amountKopecks,
        @Schema(description = "Свободный остаток после операции, в копейках") Long balanceAfterKopecks,
        @Schema(description = "Статус: DONE, PENDING, SENT, CONFIRMED, REJECTED, CANCELLED") String status,
        @Schema(description = "Человекочитаемый статус", example = "Проведена") String statusDescription,
        @Schema(description = "Чей кошелёк") String ownerName,
        Long ownerId,
        @Schema(description = "Откуда ушли деньги") FlowPointDTO source,
        @Schema(description = "Куда пришли деньги") FlowPointDTO destination,
        @Schema(description = "Объявление, если операция про него; null — объявление удалено или операция общая") UUID campaignId,
        String campaignTitle,
        String campaignPublicId,
        @Schema(description = "Кто провёл операцию; null — учётка удалена или операция автоматическая") String actorName,
        String comment,
        String createdAt
) {
    public static WalletTransactionDTO from(WalletTransaction transaction, Transfer transfer) {
        var campaign = transaction.getCampaign();
        var actor = transaction.getActor();
        User owner = transaction.getWallet() != null ? transaction.getWallet().getUser() : null;
        MoneyFlowDTO flow = MoneyFlowDTO.of(transaction, transfer);
        return new WalletTransactionDTO(
                transaction.getId(),
                transaction.getType() != null ? transaction.getType().name() : null,
                transaction.getType() != null ? transaction.getType().getDescription() : null,
                transaction.getAmountKopecks(),
                transaction.getBalanceAfterKopecks(),
                transaction.getStatus() != null ? transaction.getStatus().name() : null,
                transaction.getStatus() != null ? transaction.getStatus().getDescription() : null,
                owner != null ? owner.getName() : null,
                owner != null ? owner.getId() : null,
                flow.source(),
                flow.destination(),
                campaign != null ? campaign.getId() : null,
                campaign != null ? campaign.getTitle() : null,
                campaign != null ? campaign.getPublicId() : null,
                actor != null ? actor.getName() : null,
                transaction.getComment(),
                transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : null);
    }
}
