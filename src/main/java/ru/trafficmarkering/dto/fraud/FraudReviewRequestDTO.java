package ru.trafficmarkering.dto.fraud;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.fraud.FraudDecision;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Решение админа по отклику")
public class FraudReviewRequestDTO {

    @NotNull(message = "Решение обязательно: VERIFIED, FRAUD или AUTO")
    @Schema(description = "VERIFIED — честно, FRAUD — накрутка, AUTO — вернуть автоматике", required = true)
    private FraudDecision decision;

    @Size(max = 2000, message = "Комментарий не длиннее 2000 символов")
    private String comment;
}
