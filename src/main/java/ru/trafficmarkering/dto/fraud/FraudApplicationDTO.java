package ru.trafficmarkering.dto.fraud;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ViewSnapshotDTO;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.profile.CreatorProfile;

@Schema(description = "Отклик в очереди антифрода: сам отклик, последний замер и решение админа")
public record FraudApplicationDTO(
        ApplicationDTO application,
        String creatorEmail,
        @Schema(description = "Подписчики аккаунта криатора на площадке ролика; null — неизвестно") Long creatorFollowers,
        @Schema(description = "Последний замер с метриками; null — замеров ещё не было") ViewSnapshotDTO latestSnapshot,
        @Schema(description = "Начислено, но ещё не зачислено в кошелёк, в копейках") Long uncreditedKopecks,
        String fraudCheckedAt,
        String fraudReviewedAt,
        @Schema(description = "Имя админа, принявшего решение") String fraudReviewedBy,
        String fraudReviewComment
) {
    public static FraudApplicationDTO from(Application application,
                                           CreatorProfile profile,
                                           Long followers,
                                           ApplicationViewSnapshot latest) {
        return new FraudApplicationDTO(
                ApplicationDTO.from(application, profile),
                application.getCreator() != null ? application.getCreator().getUsername() : null,
                followers,
                latest != null ? ViewSnapshotDTO.from(latest) : null,
                Math.max(0L, application.uncreditedKopecks()),
                application.getFraudCheckedAt() != null ? application.getFraudCheckedAt().toString() : null,
                application.getFraudReviewedAt() != null ? application.getFraudReviewedAt().toString() : null,
                application.getFraudReviewedBy() != null ? application.getFraudReviewedBy().getName() : null,
                application.getFraudReviewComment());
    }
}
