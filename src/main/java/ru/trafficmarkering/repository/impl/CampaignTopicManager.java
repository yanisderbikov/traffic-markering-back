package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.campaign.CampaignTopic;
import ru.trafficmarkering.repository.GetterCampaignTopic;
import ru.trafficmarkering.repository.SaverCampaignTopic;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class CampaignTopicManager implements GetterCampaignTopic, SaverCampaignTopic {

    private final CampaignTopicRepo campaignTopicRepo;

    @Override
    public Optional<CampaignTopic> getByCode(String code) {
        try {
            return campaignTopicRepo.findById(code);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<CampaignTopic> getByNormalizedName(String normalizedName) {
        try {
            return campaignTopicRepo.findByNormalizedName(normalizedName);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean existsByCode(String code) {
        try {
            return campaignTopicRepo.existsById(code);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<CampaignTopic> search(String normalizedQuery, int limit) {
        String escaped = normalizedQuery.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        try {
            return campaignTopicRepo.search("%" + escaped + "%", escaped + "%", limit);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    /**
     * Нарушение уникальности пробрасываем как есть: два заказчика одновременно добавили
     * одну и ту же тематику — сервис отвечает на это 409, а не «ошибкой базы».
     */
    @Override
    public CampaignTopic save(CampaignTopic topic) {
        try {
            return campaignTopicRepo.saveAndFlush(topic);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
