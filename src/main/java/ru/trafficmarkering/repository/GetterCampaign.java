package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.campaign.Campaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterCampaign {

    Optional<Campaign> getById(UUID id);

    /** Ищет объявление по короткому публичному номеру — по нему ходит фронт и внешние ссылки. */
    Optional<Campaign> getByPublicId(String publicId);

    /** Объявления заказчика, новые сверху. */
    List<Campaign> getByCustomerId(Long customerId);

    /** Объявления для публичной доски: только ACTIVE, новые сверху. */
    List<Campaign> getActive();

    /** Нужна генератору публичных номеров, чтобы не выдать занятый. */
    boolean existsByPublicId(String publicId);
}
