package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;

import java.util.Map;

@Schema(description = "Замер просмотров ролика в конкретный момент")
public record ViewSnapshotDTO(
        @Schema(description = "Когда сняли замер, ISO-8601") String capturedAt,
        @Schema(description = "Просмотры на момент замера") Long views,
        @Schema(description = "Просмотры по странам (ISO 3166-1 alpha-2); null — площадка географию не отдала") Map<String, Long> countryViews,
        @Schema(description = "Откуда взяли: YOUTUBE_API, TIKTOK_API, INSTAGRAM_API, MANUAL") String source,
        @Schema(description = "Человекочитаемый источник") String sourceDescription
) {
    public static ViewSnapshotDTO from(ApplicationViewSnapshot snapshot) {
        return new ViewSnapshotDTO(
                snapshot.getCapturedAt() != null ? snapshot.getCapturedAt().toString() : null,
                snapshot.getViews(),
                snapshot.getCountryViews(),
                snapshot.getSource() != null ? snapshot.getSource().name() : null,
                snapshot.getSource() != null ? snapshot.getSource().getDescription() : null);
    }
}
