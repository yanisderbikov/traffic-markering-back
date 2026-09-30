package ru.trafficmarkering.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.repository.ApplicationDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;

import ru.trafficmarkering.model.fraud.FraudStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class ApplicationManager implements GetterApplication, SaverApplication, ApplicationDeleter {

    private final ApplicationRepo applicationRepo;
    private final EntityManager entityManager;

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
    public Optional<Application> getByIdForUpdate(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            Application application = entityManager.find(Application.class, id);
            if (application == null) {
                return Optional.empty();
            }
            entityManager.refresh(application, LockModeType.PESSIMISTIC_WRITE);
            return Optional.of(application);
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
    public long countActiveByCampaignIdAndCreatorId(UUID campaignId, Long creatorId) {
        try {
            return applicationRepo.countByCampaignIdAndCreatorId(campaignId, creatorId, ApplicationStatus.REJECTED);
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
    public Optional<Application> getInProgress(UUID campaignId, Long creatorId) {
        try {
            return applicationRepo.findByCampaignIdAndCreatorIdAndStatus(
                    campaignId, creatorId, ApplicationStatus.IN_PROGRESS).stream().findFirst();
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
    public List<Application> getCreditable() {
        try {
            return applicationRepo.findCreditable();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Application> getAccruable() {
        try {
            return applicationRepo.findByStatusIn(List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Application> getByFraudStatusIn(Collection<FraudStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        try {
            return applicationRepo.findByFraudStatusIn(statuses);
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
