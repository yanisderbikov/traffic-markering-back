package ru.trafficmarkering.repository;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public interface GetterPlatformStats {

    /** Считает всё одним запросом прямо в базе: тянуть таблицы в память ради сумм незачем. */
    PlatformStats getStats(Instant since);

    /** Ряд по шагам от шага, в который попадает from, до шага, в который попадает to. */
    List<PlatformSeriesPoint> getSeries(Instant from, Instant to, SeriesStep step, ZoneId zone);

    /** Когда на площадке появилась первая учётка — начало ряда «за всё время». */
    Optional<Instant> getFirstActivityAt();
}
