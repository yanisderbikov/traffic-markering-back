package ru.trafficmarkering.service.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.dto.admin.PlatformDashboardDTO;
import ru.trafficmarkering.dto.admin.PlatformSeriesDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.repository.GetterPlatformStats;
import ru.trafficmarkering.repository.SeriesStep;
import ru.trafficmarkering.service.admin.PlatformDashboardService;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
class PlatformDashboardServiceImpl implements PlatformDashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 365;
    static final int MAX_POINTS = 400;

    private final GetterPlatformStats getterPlatformStats;
    private final CurrentUserService currentUserService;
    private final CommissionProperties commissionProperties;

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardDTO dashboard(Integer days) {
        currentUserService.require(Role.SUPER_ADMIN);
        int periodDays = requirePeriod(days == null ? DEFAULT_DAYS : days);
        Instant now = Instant.now();
        return PlatformDashboardDTO.from(
                getterPlatformStats.getStats(now.minus(Duration.ofDays(periodDays))),
                periodDays, now, commissionProperties.getPercent());
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformSeriesDTO series(Integer days, SeriesStep step, String timeZone) {
        currentUserService.require(Role.SUPER_ADMIN);
        if (step == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите шаг: DAY, WEEK или MONTH");
        }
        ZoneId zone = requireZone(timeZone);
        Instant now = Instant.now();
        Instant from = days != null
                ? now.minus(Duration.ofDays(requirePeriod(days)))
                : getterPlatformStats.getFirstActivityAt().filter(first -> first.isBefore(now)).orElse(now);
        if (step.maxBuckets(Duration.between(from, now)) > MAX_POINTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Слишком мелкий шаг для такого периода — выберите шаг крупнее");
        }
        return PlatformSeriesDTO.from(step, zone.getId(), getterPlatformStats.getSeries(from, now, step, zone));
    }

    private static int requirePeriod(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Период — от 1 до " + MAX_DAYS + " дней");
        }
        return days;
    }

    private static ZoneId requireZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный часовой пояс: " + timeZone);
        }
    }
}
