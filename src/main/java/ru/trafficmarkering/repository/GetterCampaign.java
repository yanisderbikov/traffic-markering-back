package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.Region;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterCampaign {

    Optional<Campaign> getById(UUID id);

    /**
     * Только регион, отдельным скалярным запросом мимо identity map — в отличие от
     * {@link #getById}, гарантированно видит уже закоммиченное значение, даже если объявление
     * уже загружено (или менялось) в этой же транзакции/сессии. Нужен там, где регион читают
     * непосредственно перед начислением по гео-разбивке: старт синка и коммит смены региона
     * могут пересечься, и без этого можно применить разбивку, посчитанную под старый регион.
     */
    Optional<Region> getRegionById(UUID id);

    /** Ищет объявление по короткому публичному номеру — по нему ходит фронт и внешние ссылки. */
    Optional<Campaign> getByPublicId(String publicId);

    /** Объявления заказчика, новые сверху. */
    List<Campaign> getByCustomerId(Long customerId);

    /** Объявления для публичной доски: только ACTIVE, новые сверху. */
    List<Campaign> getActive();

    /** Нужна генератору публичных номеров, чтобы не выдать занятый. */
    boolean existsByPublicId(String publicId);
}
