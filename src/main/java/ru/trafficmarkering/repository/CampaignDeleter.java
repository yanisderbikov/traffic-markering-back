package ru.trafficmarkering.repository;

import java.util.UUID;

public interface CampaignDeleter {
    void deleteById(UUID id);
}
