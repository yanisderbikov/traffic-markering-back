package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.repository.CampaignDeleter;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.SaverCampaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class CampaignManager implements GetterCampaign, SaverCampaign, CampaignDeleter {

    private final CampaignRepo campaignRepo;

    @Override
    public Optional<Campaign> getById(UUID id) {
        try {
            return campaignRepo.findByIdWithCustomer(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Campaign> getByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        try {
            return campaignRepo.findByPublicIdWithCustomer(publicId.trim());
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Campaign> getByCustomerId(Long customerId) {
        try {
            return campaignRepo.findByCustomerId(customerId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Campaign> getActive() {
        try {
            return campaignRepo.findByStatus(CampaignStatus.ACTIVE);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean existsByPublicId(String publicId) {
        try {
            return campaignRepo.existsByPublicId(publicId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Campaign save(Campaign campaign) {
        try {
            // saveAndFlush, а не save: сервис тут же собирает DTO из возвращённой сущности,
            // а @CreationTimestamp/@UpdateTimestamp Hibernate проставляет только на flush —
            // без него createdAt/updatedAt уезжают в ответ пустыми или устаревшими
            return campaignRepo.saveAndFlush(campaign);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        try {
            campaignRepo.deleteById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
