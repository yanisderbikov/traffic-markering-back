package ru.trafficmarkering.dto.fraud;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.service.fraud.CreatorTrustService.TrustStats;

@Schema(description = "Криатор с репутацией и историей проверок")
public record CreatorTrustDTO(
        Long userId,
        String name,
        String email,
        @Schema(description = "NEW, TRUSTED, RESTRICTED, BLOCKED") String trustLevel,
        String trustLevelDescription,
        @Schema(description = "Уровень выставлен админом руками") Boolean manual,
        String note,
        String updatedAt,
        @Schema(description = "Кто менял; null — автоматика") String updatedBy,
        @Schema(description = "Подтверждённых накруток") Integer strikes,
        @Schema(description = "Роликов на проверке") Integer suspicious,
        @Schema(description = "Чистых оплаченных роликов") Integer cleanPaid,
        @Schema(description = "Всего откликов") Integer totalApplications,
        String registeredAt
) {
    public static CreatorTrustDTO from(User creator, CreatorProfile profile, TrustStats stats) {
        TrustLevel level = profile != null ? profile.trustLevel() : TrustLevel.NEW;
        User updatedBy = profile != null ? profile.getTrustUpdatedBy() : null;
        return new CreatorTrustDTO(
                creator.getId(),
                creator.getName(),
                creator.getUsername(),
                level.name(),
                level.getDescription(),
                profile != null && profile.isTrustManual(),
                profile != null ? profile.getTrustNote() : null,
                profile != null && profile.getTrustUpdatedAt() != null ? profile.getTrustUpdatedAt().toString() : null,
                updatedBy != null ? updatedBy.getName() : null,
                stats.strikes(),
                stats.suspicious(),
                stats.cleanPaid(),
                stats.total(),
                creator.getCreatedAt() != null ? creator.getCreatedAt().toString() : null);
    }
}
