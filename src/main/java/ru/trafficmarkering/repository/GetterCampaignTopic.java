package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.campaign.CampaignTopic;

import java.util.List;
import java.util.Optional;

public interface GetterCampaignTopic {

    Optional<CampaignTopic> getByCode(String code);

    Optional<CampaignTopic> getByNormalizedName(String normalizedName);

    /** Нужна генератору кодов, чтобы не выдать занятый. */
    boolean existsByCode(String code);

    /**
     * Тематики, в нормализованном названии которых есть подстрока: сначала те, что с неё начинаются,
     * дальше — по числу запущенных объявлений, при равенстве базовые раньше добавленных.
     * Пустая подстрока — просто самые популярные.
     */
    List<CampaignTopic> search(String normalizedQuery, int limit);
}
