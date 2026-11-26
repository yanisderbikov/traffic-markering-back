package ru.trafficmarkering.dto.admin;

import ru.trafficmarkering.repository.PlatformSeriesPoint;
import ru.trafficmarkering.repository.SeriesStep;

import java.util.List;

/**
 * Ряд дашборда супер-админа по шагам. Значения каждой точки — события внутри шага,
 * кроме viewsTotal: это накопленные просмотры опубликованных роликов на конец шага.
 * Суммы в копейках.
 */
public record PlatformSeriesDTO(
        String step,
        String timeZone,
        List<Point> points
) {
    public record Point(
            String start,
            String end,
            long creators,
            long customers,
            long publishedVideos,
            long views,
            long viewsTotal,
            long adTurnoverKopecks,
            long topUpsKopecks,
            long commissionKopecks,
            long partnerRewardsKopecks,
            long platformIncomeKopecks
    ) {
        static Point from(PlatformSeriesPoint p) {
            return new Point(
                    p.start().toString(),
                    p.end().toString(),
                    p.creators(),
                    p.customers(),
                    p.publishedVideos(),
                    // Замер может уменьшиться (площадка пересчитала накрутку) — в минус прирост не уводим.
                    Math.max(0L, p.viewsAtEnd() - p.viewsAtStart()),
                    p.viewsAtEnd(),
                    p.adTurnoverKopecks(),
                    p.topUpsKopecks(),
                    p.commissionKopecks(),
                    p.partnerRewardsKopecks(),
                    p.commissionKopecks() - p.partnerRewardsKopecks());
        }
    }

    public static PlatformSeriesDTO from(SeriesStep step, String timeZone, List<PlatformSeriesPoint> points) {
        return new PlatformSeriesDTO(step.name(), timeZone, points.stream().map(Point::from).toList());
    }
}
