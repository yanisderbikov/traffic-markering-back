package ru.trafficmarkering.model.application;

/**
 * Судьба одной надбавки (ссылка в профиле или промокод в закрепе) у отклика «ссылка и промокод».
 * <ul>
 *     <li>NOT_SELECTED — криатор её не выбрал или отказался от неё: ставка без этой надбавки.</li>
 *     <li>PENDING — выбрана, ждёт проверки модератором или заказчиком; бюджет под неё уже занят.</li>
 *     <li>VERIFIED — подтверждена, но условна: размещение надо сохранять до конца срока.</li>
 *     <li>EARNED — срок размещения прошёл, надбавка засчитана и уходит в кошелёк. Окончательна.</li>
 *     <li>REJECTED — не подтверждена: криатор может исправить размещение или отказаться от надбавки.</li>
 * </ul>
 */
public enum PlacementState {
    NOT_SELECTED("Не выбрано"),
    PENDING("Ждёт проверки"),
    VERIFIED("Подтверждено, держите до конца срока"),
    EARNED("Засчитано"),
    REJECTED("Не подтверждено");

    private final String description;

    PlacementState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean selected() {
        return this != NOT_SELECTED;
    }

    /** Надбавка входит в начисление и занимает бюджет объявления, пока по ней нет отказа. */
    public boolean reservesBudget() {
        return this == PENDING || this == VERIFIED || this == EARNED;
    }

    /** В кошелёк уходит только засчитанная надбавка: до конца срока размещения она условна. */
    public boolean creditable() {
        return this == EARNED;
    }

    /** Отказаться от надбавки или исправить доказательство можно, пока по ней нет подтверждения. */
    public boolean revisable() {
        return this == PENDING || this == REJECTED;
    }
}
