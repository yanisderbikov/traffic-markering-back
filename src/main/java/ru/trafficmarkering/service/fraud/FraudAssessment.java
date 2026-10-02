package ru.trafficmarkering.service.fraud;

import ru.trafficmarkering.model.fraud.FraudFlag;

import java.util.List;

/** Итог скоринга: сумма баллов (0–100) и список сработавших правил. */
public record FraudAssessment(int score, List<FraudFlag> flags) {

    public static FraudAssessment clean() {
        return new FraudAssessment(0, List.of());
    }

    public boolean has(String code) {
        return flags.stream().anyMatch(flag -> flag.code().equals(code));
    }
}
