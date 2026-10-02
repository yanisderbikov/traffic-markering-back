package ru.trafficmarkering.dto.fraud;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.fraud.FraudFlag;

@Schema(description = "Сработавшее правило антифрода")
public record FraudFlagDTO(
        @Schema(description = "Код правила", example = "LOW_ENGAGEMENT") String code,
        @Schema(description = "Название правила", example = "Слишком мало лайков") String title,
        @Schema(description = "Сколько баллов добавило") Integer points,
        @Schema(description = "Пояснение с цифрами") String detail
) {
    public static FraudFlagDTO from(FraudFlag flag) {
        return new FraudFlagDTO(flag.code(), flag.title(), flag.points(), flag.detail());
    }
}
