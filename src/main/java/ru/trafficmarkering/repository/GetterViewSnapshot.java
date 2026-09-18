package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.application.ApplicationViewSnapshot;

import java.util.List;
import java.util.UUID;

public interface GetterViewSnapshot {

    List<ApplicationViewSnapshot> getByApplicationId(UUID applicationId);
}
