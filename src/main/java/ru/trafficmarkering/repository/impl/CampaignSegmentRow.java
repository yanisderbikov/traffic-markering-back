package ru.trafficmarkering.repository.impl;

/**
 * Строка нативного запроса {@link CampaignRepo#segmentStats}. Публичный: проекцию Spring Data
 * оборачивает в прокси, а у прокси непубличного интерфейса бывают проблемы с загрузчиком классов.
 */
public interface CampaignSegmentRow {

    Long getCampaigns();

    Double getMedianRate();

    Double getMedianBudget();

    Long getLowerRate();

    Long getLowerBudget();
}
