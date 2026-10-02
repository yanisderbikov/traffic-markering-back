package ru.trafficmarkering.model;

import java.util.EnumSet;
import java.util.Set;

import static ru.trafficmarkering.model.CabinetTab.APPLICATIONS;
import static ru.trafficmarkering.model.CabinetTab.BOARD;
import static ru.trafficmarkering.model.CabinetTab.CAMPAIGNS;
import static ru.trafficmarkering.model.CabinetTab.CREATOR_TRUST;
import static ru.trafficmarkering.model.CabinetTab.CUSTOMER_WALLETS;
import static ru.trafficmarkering.model.CabinetTab.EARNINGS;
import static ru.trafficmarkering.model.CabinetTab.FRAUD;
import static ru.trafficmarkering.model.CabinetTab.MODERATION;
import static ru.trafficmarkering.model.CabinetTab.OPERATIONS;
import static ru.trafficmarkering.model.CabinetTab.OVERVIEW;
import static ru.trafficmarkering.model.CabinetTab.PAYOUTS;
import static ru.trafficmarkering.model.CabinetTab.PROFILE;
import static ru.trafficmarkering.model.CabinetTab.SOCIALS;
import static ru.trafficmarkering.model.CabinetTab.TOP_UPS;
import static ru.trafficmarkering.model.CabinetTab.USERS;
import static ru.trafficmarkering.model.CabinetTab.WALLET;

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

    public Set<CabinetTab> tabs() {
        return switch (this) {
            case CUSTOMER -> EnumSet.of(OVERVIEW, CAMPAIGNS, WALLET, PROFILE);
            case CREATOR -> EnumSet.of(OVERVIEW, BOARD, APPLICATIONS, EARNINGS, PROFILE, SOCIALS);
            case FINANCE_MANAGER -> EnumSet.of(OVERVIEW, CUSTOMER_WALLETS, TOP_UPS, PAYOUTS, OPERATIONS);
            case ADMIN -> EnumSet.of(OVERVIEW, BOARD, CAMPAIGNS, APPLICATIONS, WALLET, EARNINGS,
                    MODERATION, FRAUD, CREATOR_TRUST, PROFILE, SOCIALS);
            case SUPER_ADMIN -> EnumSet.of(OVERVIEW, CUSTOMER_WALLETS, TOP_UPS, PAYOUTS, OPERATIONS,
                    MODERATION, FRAUD, CREATOR_TRUST, USERS);
            case SERVICE -> EnumSet.noneOf(CabinetTab.class);
        };
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
