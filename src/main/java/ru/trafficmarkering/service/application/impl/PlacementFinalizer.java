package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.Placement;
import ru.trafficmarkering.model.application.PlacementCheck;
import ru.trafficmarkering.model.application.PlacementState;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.notify.ApplicationNotifications;

import java.time.Instant;
import java.util.UUID;

/**
 * Конец срока размещения одной работы — в своей транзакции, чтобы сбой проверки одной работы
 * не откатывал остальные. Ссылку перед зачётом проверяем ещё раз: подтверждённое «нет ссылки»
 * возвращает надбавку на проверку и зовёт криатора её вернуть; «не удалось проверить» — не штраф.
 * Промокод автоматически не проверить, поэтому он засчитывается по подтверждению модератора.
 */
@Component
@RequiredArgsConstructor
class PlacementFinalizer {

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final ProfileLinkChecker profileLinkChecker;
    private final ApplicationNotifications notifications;

    @Transactional
    public boolean finalizeOne(UUID applicationId) {
        Application application = getterApplication.getByIdForUpdate(applicationId).orElse(null);
        if (application == null || application.getPlacementRetainUntil() == null
                || application.getPlacementRetainUntil().isAfter(Instant.now())
                || !application.isAccruable()) {
            return false;
        }
        boolean changed = false;
        Placement link = application.link();
        if (link.state() == PlacementState.VERIFIED) {
            if (profileLinkChecker.check(application) == PlacementCheck.MISSING) {
                link.decide(PlacementState.PENDING,
                        "В конце срока размещения ссылки в профиле не нашли: " + link.getCheckDetail(),
                        null, Instant.now());
                notifications.linkMissingAtRetentionEnd(application);
            } else {
                link.setState(PlacementState.EARNED);
            }
            changed = true;
        }
        Placement promo = application.promo();
        if (promo.state() == PlacementState.VERIFIED) {
            promo.setState(PlacementState.EARNED);
            changed = true;
        }
        if (changed) {
            saverApplication.save(application);
        }
        return changed;
    }
}
