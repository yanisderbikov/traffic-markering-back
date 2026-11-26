package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.trafficmarkering.model.studio.StudioTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudioTemplateRepository extends JpaRepository<StudioTemplate, UUID> {

    List<StudioTemplate> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<StudioTemplate> findByIdAndUserId(UUID id, Long userId);

    long countByUserId(Long userId);
}
