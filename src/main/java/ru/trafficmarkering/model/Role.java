package ru.trafficmarkering.model;

import java.util.EnumSet;
import java.util.Set;

public enum Role {
    CUSTOMER("Заказчик"),
    CREATOR("Криатор"),
    FINANCE_MANAGER("Менеджер финансов"),
    ADMIN("Администратор"),
    SUPER_ADMIN("Супер-админ"),
    /** Не человек, а внешний сервис-анализатор просмотров; в БД не хранится — только в JWT */
    SERVICE("Сервис");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public Set<Role> implied() {
        return switch (this) {
            case SUPER_ADMIN -> EnumSet.of(SUPER_ADMIN, ADMIN, FINANCE_MANAGER, CUSTOMER, CREATOR);
            case ADMIN -> EnumSet.of(ADMIN, CUSTOMER, CREATOR);
            default -> EnumSet.of(this);
        };
    }

    public boolean implies(Role role) {
        return implied().contains(role);
    }

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    public static Set<Role> selfRegistrable() {
        return EnumSet.of(CUSTOMER, CREATOR);
    }

    public static Set<Role> assignable() {
        return EnumSet.of(CUSTOMER, CREATOR, FINANCE_MANAGER, ADMIN);
    }
}
