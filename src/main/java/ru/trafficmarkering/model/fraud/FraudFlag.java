package ru.trafficmarkering.model.fraud;

/**
 * Один сработавший признак накрутки: код правила, сколько баллов оно добавило
 * и человекочитаемое пояснение с цифрами, по которым сработало.
 */
public record FraudFlag(String code, String title, int points, String detail) {
}
