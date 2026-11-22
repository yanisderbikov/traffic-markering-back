package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.trafficmarkering.model.studio.StudioProfile;

import java.util.Optional;

public interface StudioProfileRepository extends JpaRepository<StudioProfile, Long> {

    Optional<StudioProfile> findByTelegramId(Long telegramId);

    Optional<StudioProfile> findByLinkCode(String linkCode);
}
