package ru.trafficmarkering.model.campaign;

public enum MaterialKind {
    FILE("Файл"),
    LINK("Ссылка");

    private final String description;

    MaterialKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
