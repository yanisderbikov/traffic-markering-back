package ru.trafficmarkering.model;

public enum Role {
    CUSTOMER,
    CREATOR,
    ADMIN,
    /** Не человек, а внешний сервис-анализатор просмотров; в БД не хранится — только в JWT */
    SERVICE
}
