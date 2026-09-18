package ru.trafficmarkering.dto.earnings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Кошелёк криатора: сколько доступно к выводу и что уже было")
public record CreatorWalletDTO(
        Long userId,
        @Schema(description = "Доступно к выводу, в копейках") Long balanceKopecks,
        @Schema(description = "Зарезервировано в незакрытых заявках на вывод, в копейках") Long reservedKopecks,
        @Schema(description = "Выведено по подтверждённым заявкам, в копейках") Long paidOutKopecks,
        @Schema(description = "Всего зачислено в кошелёк за просмотры, в копейках") Long earnedKopecks,
        @Schema(description = "Начислено по откликам, но ещё не в кошельке: ждёт порога вывода объявления или ночного зачисления, в копейках") Long pendingKopecks,
        @Schema(description = "Есть ли доступные деньги на заявку") boolean payoutAvailable,
        String updatedAt
) {
}
