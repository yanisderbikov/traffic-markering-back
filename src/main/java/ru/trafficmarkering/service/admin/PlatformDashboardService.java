package ru.trafficmarkering.service.admin;

import ru.trafficmarkering.dto.admin.PlatformDashboardDTO;
import ru.trafficmarkering.dto.admin.PlatformSeriesDTO;
import ru.trafficmarkering.repository.SeriesStep;

public interface PlatformDashboardService {

    /** Сводка площадки: значения за всё время и прирост за последние days дней. */
    PlatformDashboardDTO dashboard(Integer days);

    /**
     * Ряд для графиков за последние days дней (null — за всё время) с шагом step,
     * сутки и недели режутся в часовом поясе timeZone (null — UTC).
     */
    PlatformSeriesDTO series(Integer days, SeriesStep step, String timeZone);
}
