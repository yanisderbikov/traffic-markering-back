package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Transfer;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Перевод вне платформы по операции: адрес TRON, номер транзакции, скриншоты, кто и когда")
public record TransferDTO(
        @Schema(description = "Адрес кошелька TRON, куда ушли USDT; null для пополнения") String tronAddress,
        @Schema(description = "Номер (хеш) транзакции в сети TRON") String txId,
        @Schema(description = "Комментарий финансиста") String financeComment,
        @Schema(description = "Почему операция отклонена; null — не отклонена") String rejectReason,
        @Schema(description = "Скриншоты перевода: ключ и временная ссылка") List<ProofDTO> proofs,
        @Schema(description = "Финансист, который провёл или отклонил") String processedByName,
        @Schema(description = "Владелец кошелька: криатор для выплаты, заказчик для пополнения и вывода") Long ownerId,
        String ownerName,
        String ownerEmail,
        String sentAt,
        String confirmedAt,
        String closedAt
) {
    public record ProofDTO(String key, String url) {
    }

    public static TransferDTO from(Transfer transfer, User owner, Function<String, String> proofUrl) {
        var processedBy = transfer.getProcessedBy();
        return new TransferDTO(
                transfer.getTronAddress(),
                transfer.getTxId(),
                transfer.getFinanceComment(),
                transfer.getRejectReason(),
                transfer.getProofKeys().stream().map(key -> new ProofDTO(key, proofUrl.apply(key))).toList(),
                processedBy != null ? processedBy.getName() : null,
                owner != null ? owner.getId() : null,
                owner != null ? owner.getName() : null,
                owner != null ? owner.getUsername() : null,
                transfer.getSentAt() != null ? transfer.getSentAt().toString() : null,
                transfer.getConfirmedAt() != null ? transfer.getConfirmedAt().toString() : null,
                transfer.getClosedAt() != null ? transfer.getClosedAt().toString() : null);
    }
}
