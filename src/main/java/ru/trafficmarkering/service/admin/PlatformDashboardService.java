package ru.trafficmarkering.service.admin;

import ru.trafficmarkering.dto.admin.PlatformDashboardDTO;

public interface PlatformDashboardService {

    /** Сводка площадки: значения за всё время и прирост за последние days дней. */
    PlatformDashboardDTO dashboard(Integer days);
}
