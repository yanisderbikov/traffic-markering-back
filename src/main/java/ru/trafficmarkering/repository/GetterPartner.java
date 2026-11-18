package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.partner.Partner;

import java.util.Optional;

public interface GetterPartner {

    Optional<Partner> getByUserId(Long userId);

    Optional<Partner> getByCode(String code);

    boolean existsByCode(String code);
}
