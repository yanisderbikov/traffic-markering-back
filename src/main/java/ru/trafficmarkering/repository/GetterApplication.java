package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.application.Application;

import ru.trafficmarkering.model.fraud.FraudStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterApplication {

    /**
     * Отклики объявления в порядке появления. Порядок важен не для красоты:
     * по нему CampaignAccrualService детерминированно раздаёт бюджет — кто раньше
     * откликнулся, тот раньше и получает начисление.
     */
    List<Application> getByCampaignIdOrderByCreatedAt(UUID campaignId);

    Optional<Application> getById(UUID id);

    Optional<Application> getByIdForUpdate(UUID id);

    /** Отклики криатора, новые сверху. */
    List<Application> getByCreatorId(Long creatorId);

    /** Сколько откликов у объявления: по нему заказчику запрещают удалять объявление. */
    long countByCampaignId(UUID campaignId);

    long countActiveByCampaignIdAndCreatorId(UUID campaignId, Long creatorId);

    /**
     * Отклики в работе (статус APPROVED) — их обходит синхронизация просмотров.
     * COMPLETED сюда не попадает: по завершённому отклику просмотры уже не догоняем.
     */
    List<Application> getApproved();

    /**
     * Уже поданный ролик: один и тот же видос нельзя подать дважды.
     * Отклонённые не в счёт — их ролик снова свободен.
     */
    Optional<Application> getActiveByVideoKey(String videoKey);

    /** Взятый в работу оффер без ролика: повторное «взять в работу» возвращает его, а не плодит новые. */
    Optional<Application> getInProgress(UUID campaignId, Long creatorId);

    /** Проверка занятости публичного номера для {@link ru.trafficmarkering.util.PublicIdGenerator}. */
    boolean existsByPublicId(String publicId);

    List<Application> getCreditable();

    /** Отклики, по которым идут деньги (APPROVED и COMPLETED): их перепроверяет антифрод. */
    List<Application> getAccruable();

    /** Отклики с вердиктом антифрода из набора — очередь подозрительных роликов. */
    List<Application> getByFraudStatusIn(Collection<FraudStatus> statuses);
}
