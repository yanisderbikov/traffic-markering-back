package ru.trafficmarkering.service.fraud;

import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.fraud.TrustLevel;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Репутация креаторов: уровень доверия и его автоматический пересчёт. */
public interface CreatorTrustService {

    /** Сколько подтверждённых накруток, подозрительных и чистых оплаченных роликов у креатора. */
    record TrustStats(int strikes, int suspicious, int cleanPaid, int total) {
    }

    TrustLevel levelOf(Long creatorId);

    /** Уровни сразу для пачки креаторов — чтобы пересчёт объявления не ходил в базу по каждому отклику. */
    Map<Long, TrustLevel> levelsOf(Collection<Long> creatorIds);

    TrustStats stats(List<Application> applications);

    /**
     * Пересчитать уровень по истории откликов: подтверждённая накрутка ограничивает,
     * две — блокируют, чистые оплаченные ролики переводят новичка в проверенные.
     * Уровень, выставленный админом руками, не трогается.
     */
    TrustLevel refresh(Long creatorId);

    /**
     * Ручное решение админа. {@code level == null} снимает ручную отметку и возвращает
     * креатора автоматике.
     */
    TrustLevel setManual(Long creatorId, TrustLevel level, String note, User admin);
}
