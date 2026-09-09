package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;

import java.util.List;
import java.util.UUID;

@Repository
interface ViewSnapshotRepo extends JpaRepository<ApplicationViewSnapshot, Long> {

    List<ApplicationViewSnapshot> findByApplicationIdOrderByCapturedAtDesc(UUID applicationId);
}
