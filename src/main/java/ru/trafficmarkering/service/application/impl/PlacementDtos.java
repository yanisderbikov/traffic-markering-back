package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.placement.ApplicationPlacementDTO;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.service.storage.FileStorage;

/** Блок «ссылка и промокод» в DTO отклика: доказательства отдаются временными ссылками хранилища. */
@Component
@RequiredArgsConstructor
class PlacementDtos {

    private final FileStorage fileStorage;

    ApplicationDTO attach(ApplicationDTO dto, Application application) {
        Campaign campaign = application.getCampaign();
        if (campaign == null || !campaign.paysBySnapshot()) {
            return dto;
        }
        return dto.withPlacement(ApplicationPlacementDTO.from(application, campaign,
                fileStorage.presignedUrl(application.link().getProofKey()),
                fileStorage.presignedUrl(application.promo().getProofKey())));
    }
}
