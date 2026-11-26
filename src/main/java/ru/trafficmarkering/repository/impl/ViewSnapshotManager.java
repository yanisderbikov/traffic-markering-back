package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverViewSnapshot;

import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class ViewSnapshotManager implements GetterViewSnapshot, SaverViewSnapshot {

    private final ViewSnapshotRepo viewSnapshotRepo;

    @Override
    public List<ApplicationViewSnapshot> getByApplicationId(UUID applicationId) {
        if (applicationId == null) {
            return List.of();
        }
        try {
            return viewSnapshotRepo.findByApplicationIdOrderByCapturedAtDesc(applicationId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public ApplicationViewSnapshot save(ApplicationViewSnapshot snapshot) {
        try {
            return viewSnapshotRepo.saveAndFlush(snapshot);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
