package ru.trafficmarkering.model.application;

import java.util.Locale;

/** Какая надбавка: рекламная ссылка в профиле или промокод в закреплённом комментарии. */
public enum PlacementKind {
    LINK("Ссылка в профиле"),
    PROMO("Промокод в закрепе");

    private final String description;

    PlacementKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Из пути ручки: link / promo в любом регистре; null — не распознано. */
    public static PlacementKind fromPath(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
