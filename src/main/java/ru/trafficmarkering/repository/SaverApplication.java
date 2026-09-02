package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.application.Application;

public interface SaverApplication {
    Application save(Application application);
}
