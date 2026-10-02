package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.partner.Partner;
import ru.trafficmarkering.repository.GetterPartner;
import ru.trafficmarkering.repository.SaverPartner;

import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class PartnerManager implements GetterPartner, SaverPartner {

    private final PartnerRepo partnerRepo;

    @Override
    public Optional<Partner> getByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return partnerRepo.findByUserId(userId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Partner> getByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return partnerRepo.findByCode(code);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean existsByCode(String code) {
        try {
            return partnerRepo.existsByCode(code);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Partner save(Partner partner) {
        try {
            return partnerRepo.saveAndFlush(partner);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
