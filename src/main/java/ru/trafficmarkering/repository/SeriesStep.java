package ru.trafficmarkering.repository;

import java.time.Duration;

/** Шаг ряда дашборда: календарные сутки, неделя с понедельника или месяц в часовом поясе зрителя. */
public enum SeriesStep {
    DAY("day", "1 day", Duration.ofDays(1)),
    WEEK("week", "1 week", Duration.ofDays(7)),
    MONTH("month", "1 month", Duration.ofDays(28));

    private final String unit;
    private final String interval;
    private final Duration shortest;

    SeriesStep(String unit, String interval, Duration shortest) {
        this.unit = unit;
        this.interval = interval;
        this.shortest = shortest;
    }

    /** Аргумент date_trunc в Postgres. */
    public String unit() {
        return unit;
    }

    /** Литерал interval в Postgres. */
    public String interval() {
        return interval;
    }

    /** Сколько шагов поместится в отрезок — с запасом, по самому короткому шагу. */
    public long maxBuckets(Duration range) {
        return range.toMillis() / shortest.toMillis() + 2;
    }
}
