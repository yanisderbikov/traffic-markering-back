package ru.trafficmarkering.service.campaign.impl;

import ru.trafficmarkering.dto.campaign.CampaignMaterialDTO;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignMaterial;
import ru.trafficmarkering.service.storage.FileStorage;

import java.util.List;

final class CampaignMaterials {

    private CampaignMaterials() {
    }

    static List<CampaignMaterialDTO> toDTO(Campaign campaign, FileStorage fileStorage) {
        return campaign.getMaterials().stream()
                .map(material -> CampaignMaterialDTO.from(material, fileUrl(material, fileStorage)))
                .toList();
    }

    private static String fileUrl(CampaignMaterial material, FileStorage fileStorage) {
        if (!material.isFile()) {
            return null;
        }
        return fileStorage.presignedUrl(material.getFileKey(), material.getTitle(), material.opensInBrowser());
    }
}
