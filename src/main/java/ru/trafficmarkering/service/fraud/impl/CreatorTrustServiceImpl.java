package ru.trafficmarkering.service.fraud.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.fraud.FraudStatus;
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.service.fraud.CreatorTrustService;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
class CreatorTrustServiceImpl implements CreatorTrustService {

    private final GetterCreatorProfile getterCreatorProfile;
    private final SaverCreatorProfile saverCreatorProfile;
    private final GetterApplication getterApplication;
    private final FraudProperties properties;

    @Override
    @Transactional(readOnly = true)
    public TrustLevel levelOf(Long creatorId) {
        return getterCreatorProfile.getByUserId(creatorId)
                .map(CreatorProfile::trustLevel)
                .orElse(TrustLevel.NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, TrustLevel> levelsOf(Collection<Long> creatorIds) {
        Map<Long, TrustLevel> levels = new HashMap<>();
        for (CreatorProfile profile : getterCreatorProfile.getAllByUserIds(creatorIds)) {
            levels.put(profile.getUser().getId(), profile.trustLevel());
        }
        return levels;
    }

    @Override
    public TrustStats stats(List<Application> applications) {
        int strikes = 0;
        int suspicious = 0;
        int cleanPaid = 0;
        for (Application application : applications) {
            FraudStatus status = application.fraudStatus();
            if (status == FraudStatus.FRAUD && application.isFraudReviewed()) {
                strikes++;
            } else if (status.needsAttention()) {
                suspicious++;
            } else if (application.getCreditedKopecks() != null && application.getCreditedKopecks() > 0) {
                cleanPaid++;
            }
        }
        return new TrustStats(strikes, suspicious, cleanPaid, applications.size());
    }

    @Override
    @Transactional
    public TrustLevel refresh(Long creatorId) {
        Optional<CreatorProfile> found = getterCreatorProfile.getByUserId(creatorId);
        if (found.isEmpty()) {
            return TrustLevel.NEW;
        }
        CreatorProfile profile = found.get();
        if (profile.isTrustManual()) {
            return profile.trustLevel();
        }
        TrustStats stats = stats(getterApplication.getByCreatorId(creatorId));
        TrustLevel computed = levelFor(stats);
        if (computed != profile.trustLevel()) {
            log.info("Репутация криатора {}: {} → {} (накруток {}, чистых оплаченных {})",
                    creatorId, profile.trustLevel(), computed, stats.strikes(), stats.cleanPaid());
            profile.setTrustLevel(computed);
            profile.setTrustUpdatedAt(Instant.now());
            profile.setTrustUpdatedBy(null);
            saverCreatorProfile.save(profile);
        }
        return computed;
    }

    @Override
    @Transactional
    public TrustLevel setManual(Long creatorId, TrustLevel level, String note, User admin) {
        CreatorProfile profile = getterCreatorProfile.getByUserId(creatorId).orElse(null);
        if (profile == null) {
            return TrustLevel.NEW;
        }
        profile.setTrustNote(note);
        profile.setTrustUpdatedAt(Instant.now());
        profile.setTrustUpdatedBy(admin);
        if (level == null) {
            profile.setTrustManual(Boolean.FALSE);
            saverCreatorProfile.save(profile);
            return refresh(creatorId);
        }
        profile.setTrustManual(Boolean.TRUE);
        profile.setTrustLevel(level);
        saverCreatorProfile.save(profile);
        return level;
    }

    private TrustLevel levelFor(TrustStats stats) {
        if (stats.strikes() >= properties.getStrikesToBlock()) {
            return TrustLevel.BLOCKED;
        }
        if (stats.strikes() >= 1) {
            return TrustLevel.RESTRICTED;
        }
        if (stats.cleanPaid() >= properties.getTrustedAfterCleanVideos()) {
            return TrustLevel.TRUSTED;
        }
        return TrustLevel.NEW;
    }
}
