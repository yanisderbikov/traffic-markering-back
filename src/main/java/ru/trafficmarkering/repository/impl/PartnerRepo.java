package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.partner.Partner;

import java.util.Optional;

@Repository
interface PartnerRepo extends JpaRepository<Partner, Long> {

    Optional<Partner> findByUserId(Long userId);

    Optional<Partner> findByCode(String code);

    boolean existsByCode(String code);
}
