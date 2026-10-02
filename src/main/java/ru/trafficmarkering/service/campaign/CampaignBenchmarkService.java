package ru.trafficmarkering.service.campaign;

import ru.trafficmarkering.dto.campaign.CampaignBenchmarkDTO;
import ru.trafficmarkering.dto.campaign.CampaignSegmentDTO;

public interface CampaignBenchmarkService {

    CampaignBenchmarkDTO getBenchmarks();

    /** Ставка и бюджет объявления против других запущенных объявлений его тематики. */
    CampaignSegmentDTO getSegment(String publicId);
}
