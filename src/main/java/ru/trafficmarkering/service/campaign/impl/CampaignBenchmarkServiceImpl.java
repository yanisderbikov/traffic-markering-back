package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.dto.campaign.CampaignBenchmarkDTO;
import ru.trafficmarkering.dto.campaign.CampaignTopicDTO;
import ru.trafficmarkering.model.campaign.CampaignTopic;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.service.campaign.CampaignBenchmarkService;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
class CampaignBenchmarkServiceImpl implements CampaignBenchmarkService {

    static final long DEFAULT_MEDIAN_RATE_PER_THOUSAND_KOPECKS = 150_00L;
    static final long DEFAULT_MEDIAN_BUDGET_KOPECKS = 100_000_00L;

    private final GetterCampaign getterCampaign;

    @Override
    @Transactional(readOnly = true)
    public CampaignBenchmarkDTO getBenchmarks() {
        return new CampaignBenchmarkDTO(
                getterCampaign.getMedianRatePerThousandKopecks().orElse(DEFAULT_MEDIAN_RATE_PER_THOUSAND_KOPECKS),
                getterCampaign.getMedianBudgetKopecks().orElse(DEFAULT_MEDIAN_BUDGET_KOPECKS),
                Arrays.stream(CampaignTopic.values()).map(CampaignTopicDTO::from).toList());
    }
}
