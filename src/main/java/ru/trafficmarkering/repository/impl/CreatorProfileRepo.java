package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.profile.CreatorProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface CreatorProfileRepo extends JpaRepository<CreatorProfile, UUID> {
    Optional<CreatorProfile> findByUserId(Long userId);

    List<CreatorProfile> findAllByUserIdIn(Collection<Long> userIds);
}
