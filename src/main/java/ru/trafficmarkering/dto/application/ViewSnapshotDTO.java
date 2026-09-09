package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;

@Schema(description = "Замер просмотров ролика в конкретный момент")
public record ViewSnapshotDTO(
        @Schema(description = "Когда сняли замер, ISO-8601") String capturedAt,
        @Schema(description = "Просмотры на момент замера") Long views,
        @Schema(description = "Откуда взяли: YOUTUBE_API, TIKTOK_API, INSTAGRAM_API, MANUAL") String source,
        @Schema(description = "Человекочитаемый источник") String sourceDescription
) {
    public static ViewSnapshotDTO from(ApplicationViewSnapshot snapshot) {
        return new ViewSnapshotDTO(
                snapshot.getCapturedAt() != null ? snapshot.getCapturedAt().toString() : null,
                snapshot.getViews(),
                snapshot.getSource() != null ? snapshot.getSource().name() : null,
                snapshot.getSource() != null ? snapshot.getSource().getDescription() : null);
    }
}
