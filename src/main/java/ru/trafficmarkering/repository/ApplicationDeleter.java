package ru.trafficmarkering.repository;

import java.util.UUID;

public interface ApplicationDeleter {
    void deleteById(UUID id);
}
