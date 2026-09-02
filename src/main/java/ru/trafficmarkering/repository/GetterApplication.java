package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.application.Application;

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

    /** Отклики криатора, новые сверху. */
    List<Application> getByCreatorId(Long creatorId);

    /** Один криатор — один отклик на объявление; на этом стоит проверка повторного отклика. */
    boolean existsByCampaignIdAndCreatorId(UUID campaignId, Long creatorId);

    /** Сколько откликов у объявления: по нему заказчику запрещают удалять объявление. */
    long countByCampaignId(UUID campaignId);

    /**
     * Отклики в работе (статус APPROVED) — их обходит синхронизация просмотров.
     * COMPLETED сюда не попадает: по завершённому отклику просмотры уже не догоняем.
     */
    List<Application> getApproved();

    /** Проверка занятости публичного номера для {@link ru.trafficmarkering.util.PublicIdGenerator}. */
    boolean existsByPublicId(String publicId);
}
