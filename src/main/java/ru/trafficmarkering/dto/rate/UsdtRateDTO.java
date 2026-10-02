package ru.trafficmarkering.dto.rate;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Курс USDT/RUB на бирже Rapira: сколько рублей стоит 1 USDT")
public record UsdtRateDTO(
        @Schema(description = "Цена покупки 1 USDT в рублях", example = "86.76") BigDecimal askPrice,
        @Schema(description = "Цена продажи 1 USDT в рублях", example = "86.73") BigDecimal bidPrice,
        @Schema(description = "Когда курс получен с биржи, ISO-8601") String fetchedAt,
        @Schema(description = "Источник курса", example = "Rapira") String source
) {
}
