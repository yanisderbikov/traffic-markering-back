package ru.trafficmarkering.repository;

import java.time.Instant;

/**
 * Один шаг ряда дашборда: события в полуинтервале [start, end). viewsAtStart и viewsAtEnd —
 * сумма последних замеров опубликованных роликов на границах шага, их разница — прирост просмотров.
 * Суммы в копейках.
 */
public record PlatformSeriesPoint(Instant start,
                                  Instant end,
                                  long creators,
                                  long customers,
                                  long publishedVideos,
                                  long viewsAtStart,
                                  long viewsAtEnd,
                                  long adTurnoverKopecks,
                                  long topUpsKopecks,
                                  long commissionKopecks,
                                  long partnerRewardsKopecks) {
}
