package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.campaign.CampaignTopic;

public interface SaverCampaignTopic {
    CampaignTopic save(CampaignTopic topic);
}
