package ru.trafficmarkering.service.partner;

import ru.trafficmarkering.dto.partner.PartnerDTO;
import ru.trafficmarkering.dto.partner.PartnerInviteDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.partner.Partner;

import java.util.Optional;

public interface PartnerService {

    PartnerDTO my();

    PartnerDTO activate();

    PartnerInviteDTO invite(String code);

    Optional<Partner> findByCode(String code);

    boolean isPartner(User user);
}
