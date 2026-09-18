package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.application.ApplicationViewSnapshot;

public interface SaverViewSnapshot {
    ApplicationViewSnapshot save(ApplicationViewSnapshot snapshot);
}
