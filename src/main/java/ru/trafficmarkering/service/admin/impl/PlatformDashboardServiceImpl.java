package ru.trafficmarkering.service.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.dto.admin.PlatformDashboardDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.repository.GetterPlatformStats;
import ru.trafficmarkering.service.admin.PlatformDashboardService;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
class PlatformDashboardServiceImpl implements PlatformDashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 365;

    private final GetterPlatformStats getterPlatformStats;
    private final CurrentUserService currentUserService;
    private final CommissionProperties commissionProperties;

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardDTO dashboard(Integer days) {
        currentUserService.require(Role.SUPER_ADMIN);
        int periodDays = days == null ? DEFAULT_DAYS : days;
        if (periodDays < 1 || periodDays > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Период — от 1 до " + MAX_DAYS + " дней");
        }
        Instant now = Instant.now();
        return PlatformDashboardDTO.from(
                getterPlatformStats.getStats(now.minus(Duration.ofDays(periodDays))),
                periodDays, now, commissionProperties.getPercent());
    }
}
