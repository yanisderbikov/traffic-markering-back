package ru.trafficmarkering.model.campaign;

/**
 * Как объявление платит за просмотры.
 * <ul>
 *     <li>CONTINUOUS — начисление растёт вместе с просмотрами одобренного ролика, ночью уезжает в кошелёк.</li>
 *     <li>SNAPSHOT — «ссылка и промокод»: просмотры фиксируются снимком при подаче ролика, ставка складывается
 *     из базы и двух независимых надбавок — за рекламную ссылку в профиле и за промокод в закреплённом
 *     комментарии. Рост просмотров после подачи эту работу не увеличивает.</li>
 * </ul>
 */
public enum PaymentModel {
    CONTINUOUS("По мере роста просмотров"),
    SNAPSHOT("Ссылка и промокод: по просмотрам на момент подачи");

    private final String description;

    PaymentModel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
