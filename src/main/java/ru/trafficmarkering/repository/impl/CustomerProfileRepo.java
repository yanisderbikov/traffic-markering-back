package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.profile.CustomerProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface CustomerProfileRepo extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByUserId(Long userId);

    List<CustomerProfile> findAllByUserIdIn(Collection<Long> userIds);
}
