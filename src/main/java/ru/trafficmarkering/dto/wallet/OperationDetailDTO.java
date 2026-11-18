package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;

import java.util.function.Function;

@Schema(description = "Операция целиком: сама проводка и, если деньги ходили вне платформы, перевод со скриншотами и подтверждениями")
public record OperationDetailDTO(
        WalletTransactionDTO transaction,
        @Schema(description = "Перевод вне платформы; null для внутренних операций и старых пополнений без документов") TransferDTO transfer
) {
    public static OperationDetailDTO from(WalletTransaction transaction,
                                          Transfer transfer,
                                          Function<String, String> proofUrl) {
        User owner = transaction.getWallet() != null ? transaction.getWallet().getUser() : null;
        return new OperationDetailDTO(
                WalletTransactionDTO.from(transaction, transfer),
                transfer != null ? TransferDTO.from(transfer, owner, proofUrl) : null);
    }
}
