package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.campaign.Campaign;

public interface SaverCampaign {
    Campaign save(Campaign campaign);
}
