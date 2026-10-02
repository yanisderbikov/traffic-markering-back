package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.partner.Partner;

public interface SaverPartner {
    Partner save(Partner partner);
}
