package ru.trafficmarkering.service.campaign;

import ru.trafficmarkering.dto.campaign.CampaignTopicCreateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignTopicDTO;

import java.util.List;

/**
 * Справочник тематик: самые популярные заказчик видит в мастере сразу, остальные находит поиском,
 * а если подходящей нет — добавляет свою.
 */
public interface CampaignTopicService {

    /** Топ по числу запущенных объявлений. */
    List<CampaignTopicDTO> getPopular();

    /** Без учёта регистра, «ё» и лишних пробелов; пустой запрос — те же самые популярные. */
    List<CampaignTopicDTO> search(String query, int limit);

    /** Такая тематика уже есть — возвращает её, дубль не заводит. */
    CampaignTopicDTO create(CampaignTopicCreateRequestDTO request);
}
