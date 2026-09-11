package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.repository.ApplicationDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class ApplicationManager implements GetterApplication, SaverApplication, ApplicationDeleter {

    private final ApplicationRepo applicationRepo;

    @Override
    public List<Application> getByCampaignIdOrderByCreatedAt(UUID campaignId) {
        try {
            return applicationRepo.findByCampaignIdOrderByCreatedAtAsc(campaignId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Application> getById(UUID id) {
        try {
            return applicationRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Application> getByCreatorId(Long creatorId) {
        try {
            return applicationRepo.findByCreatorIdOrderByCreatedAtDesc(creatorId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public long countByCampaignId(UUID campaignId) {
        try {
            return applicationRepo.countByCampaignId(campaignId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Application> getApproved() {
        try {
            return applicationRepo.findByStatus(ApplicationStatus.APPROVED);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Application> getActiveByVideoKey(String videoKey) {
        if (videoKey == null) {
            return Optional.empty();
        }
        try {
            return applicationRepo.findByVideoKey(videoKey, ApplicationStatus.REJECTED).stream().findFirst();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean existsByPublicId(String publicId) {
        try {
            return applicationRepo.existsByPublicId(publicId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Application save(Application application) {
        try {
            // saveAndFlush, а не save: сервис тут же собирает DTO из возвращённой сущности,
            // а @CreationTimestamp/@UpdateTimestamp Hibernate проставляет только на flush —
            // без него createdAt/updatedAt уезжают в ответ пустыми или устаревшими
            return applicationRepo.saveAndFlush(application);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        try {
            applicationRepo.deleteById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
