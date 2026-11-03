package ru.trafficmarkering.repository;

import java.time.Instant;

public interface GetterPlatformStats {

    /** Считает всё одним запросом прямо в базе: тянуть таблицы в память ради сумм незачем. */
    PlatformStats getStats(Instant since);
}
