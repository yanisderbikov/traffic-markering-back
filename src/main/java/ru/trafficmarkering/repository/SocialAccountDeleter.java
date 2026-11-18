package ru.trafficmarkering.repository;

import java.util.UUID;

public interface SocialAccountDeleter {
    void deleteById(UUID id);
}
