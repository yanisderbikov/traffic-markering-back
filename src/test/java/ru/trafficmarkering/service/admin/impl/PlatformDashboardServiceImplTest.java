package ru.trafficmarkering.service.admin.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.dto.admin.PlatformDashboardDTO;
import ru.trafficmarkering.dto.admin.PlatformSeriesDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.repository.GetterPlatformStats;
import ru.trafficmarkering.repository.PlatformSeriesPoint;
import ru.trafficmarkering.repository.PlatformStats;
import ru.trafficmarkering.repository.SeriesStep;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformDashboardServiceImplTest {

    private static final PlatformStats STATS = new PlatformStats(
            12, 3, 5, 1,
            40, 7, 125_000, 4,
            900_000, 150_000, 2_000_000, 300_000,
            220_000, 40_000, 2_200, 400);

    private final GetterPlatformStats getterPlatformStats = mock(GetterPlatformStats.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final CommissionProperties commissionProperties = new CommissionProperties();

    private final PlatformDashboardServiceImpl service =
            new PlatformDashboardServiceImpl(getterPlatformStats, currentUserService, commissionProperties);

    @Test
    void mapsStatsAndNetsPartnerRewardsOutOfIncome() {
        when(getterPlatformStats.getStats(any())).thenReturn(STATS);

        PlatformDashboardDTO dto = service.dashboard(null);

        verify(currentUserService).require(Role.SUPER_ADMIN);
        assertThat(dto.periodDays()).isEqualTo(30);
        assertThat(dto.commissionPercent()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(dto.creators()).isEqualTo(new PlatformDashboardDTO.Metric(12, 3));
        assertThat(dto.customers()).isEqualTo(new PlatformDashboardDTO.Metric(5, 1));
        assertThat(dto.publishedVideos()).isEqualTo(new PlatformDashboardDTO.Metric(40, 7));
        assertThat(dto.views()).isEqualTo(125_000);
        assertThat(dto.activeCampaigns()).isEqualTo(4);
        assertThat(dto.adTurnoverKopecks()).isEqualTo(new PlatformDashboardDTO.Metric(900_000, 150_000));
        assertThat(dto.commissionKopecks()).isEqualTo(new PlatformDashboardDTO.Metric(220_000, 40_000));
        assertThat(dto.platformIncomeKopecks()).isEqualTo(new PlatformDashboardDTO.Metric(217_800, 39_600));
    }

    @Test
    void periodStartsGivenDaysAgo() {
        when(getterPlatformStats.getStats(any())).thenReturn(STATS);
        Instant before = Instant.now();

        service.dashboard(7);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(getterPlatformStats).getStats(since.capture());
        assertThat(since.getValue())
                .isBetween(before.minus(Duration.ofDays(7)), Instant.now().minus(Duration.ofDays(7)));
    }

    @Test
    void rejectsPeriodOutOfRange() {
        assertThatThrownBy(() -> service.dashboard(0))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.dashboard(366)).isInstanceOf(ResponseStatusException.class);
        verify(getterPlatformStats, never()).getStats(any());
    }

    @Test
    void requiresSuperAdmin() {
        when(currentUserService.require(Role.SUPER_ADMIN))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.dashboard(30)).isInstanceOf(ResponseStatusException.class);
        verify(getterPlatformStats, never()).getStats(any());
    }

    @Test
    void seriesTurnsSnapshotBoundariesIntoViewGrowthAndNetsIncome() {
        Instant start = Instant.parse("2026-11-01T21:00:00Z");
        Instant end = Instant.parse("2026-11-02T21:00:00Z");
        when(getterPlatformStats.getSeries(any(), any(), eq(SeriesStep.DAY), eq(ZoneId.of("Europe/Moscow"))))
                .thenReturn(List.of(
                        new PlatformSeriesPoint(start, end, 2, 1, 3, 1_000, 4_500, 70_000, 200_000, 20_000, 2_000),
                        new PlatformSeriesPoint(end, end.plus(Duration.ofDays(1)), 0, 0, 0, 4_500, 4_200, 0, 0, 0, 0)));

        PlatformSeriesDTO dto = service.series(7, SeriesStep.DAY, "Europe/Moscow");

        assertThat(dto.step()).isEqualTo("DAY");
        assertThat(dto.timeZone()).isEqualTo("Europe/Moscow");
        PlatformSeriesDTO.Point first = dto.points().get(0);
        assertThat(first.start()).isEqualTo("2026-11-01T21:00:00Z");
        assertThat(first.views()).isEqualTo(3_500);
        assertThat(first.viewsTotal()).isEqualTo(4_500);
        assertThat(first.platformIncomeKopecks()).isEqualTo(18_000);
        assertThat(dto.points().get(1).views()).isZero();
    }

    @Test
    void allTimeSeriesStartsAtFirstActivityInUtcByDefault() {
        Instant first = Instant.now().minus(Duration.ofDays(100));
        when(getterPlatformStats.getFirstActivityAt()).thenReturn(Optional.of(first));
        when(getterPlatformStats.getSeries(any(), any(), any(), any())).thenReturn(List.of());

        service.series(null, SeriesStep.WEEK, null);

        verify(getterPlatformStats).getSeries(eq(first), any(), eq(SeriesStep.WEEK), eq(ZoneOffset.UTC));
    }

    @Test
    void seriesRejectsTooManyPointsBadZoneAndMissingStep() {
        when(getterPlatformStats.getFirstActivityAt())
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofDays(500))));

        assertThatThrownBy(() -> service.series(null, SeriesStep.DAY, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.series(30, SeriesStep.DAY, "Mars/Olympus"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.series(30, null, null))
                .isInstanceOf(ResponseStatusException.class);
        verify(getterPlatformStats, never()).getSeries(any(), any(), any(), any());
    }
}
