package ru.trafficmarkering.model.application;

/**
 * Итог автоматической проверки размещения по публичным данным площадки.
 * MISSING — только когда источник отдаёт нужное поле и в нём нашего нет; ошибка источника
 * или поле, которого источник не отдаёт, — это UNKNOWN, а не «удалено».
 */
public enum PlacementCheck {
    FOUND("Найдено"),
    MISSING("Не найдено"),
    UNKNOWN("Не удалось проверить");

    private final String description;

    PlacementCheck(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
