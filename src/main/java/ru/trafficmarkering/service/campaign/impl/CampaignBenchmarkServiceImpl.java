package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CampaignProperties;
import ru.trafficmarkering.dto.campaign.CampaignBenchmarkDTO;
import ru.trafficmarkering.dto.campaign.CampaignSegmentDTO;
import ru.trafficmarkering.dto.campaign.CampaignSegmentMetricDTO;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignTopic;
import ru.trafficmarkering.repository.CampaignSegmentStats;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.service.campaign.CampaignBenchmarkService;
import ru.trafficmarkering.service.campaign.CampaignTopicService;

@Service
@RequiredArgsConstructor
class CampaignBenchmarkServiceImpl implements CampaignBenchmarkService {

    static final long DEFAULT_MEDIAN_RATE_PER_THOUSAND_KOPECKS = 150_00L;
    static final long DEFAULT_MEDIAN_BUDGET_KOPECKS = 100_000_00L;

    /**
     * Меньше трёх соседей — не сегмент: медиана одного-двух объявлений ничего не говорит
     * о рынке, а заодно выдаёт точные условия конкретного конкурента.
     */
    static final int MIN_SEGMENT_CAMPAIGNS = 3;

    private final GetterCampaign getterCampaign;
    private final CampaignTopicService campaignTopicService;
    private final CampaignProperties campaignProperties;

    @Override
    @Transactional(readOnly = true)
    public CampaignBenchmarkDTO getBenchmarks() {
        return new CampaignBenchmarkDTO(
                getterCampaign.getMedianRatePerThousandKopecks().orElse(DEFAULT_MEDIAN_RATE_PER_THOUSAND_KOPECKS),
                getterCampaign.getMedianBudgetKopecks().orElse(DEFAULT_MEDIAN_BUDGET_KOPECKS),
                campaignProperties.minBudgetKopecks(),
                campaignProperties.getMaxPayoutBudgetPercent(),
                campaignTopicService.getPopular());
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignSegmentDTO getSegment(String publicId) {
        Campaign campaign = getterCampaign.getByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Объявление не найдено: " + publicId));
        Long rate = positiveOrNull(campaign.getRatePerThousandKopecks());
        Long budget = positiveOrNull(campaign.getBudgetKopecks());
        CampaignTopic topic = campaign.getTopic();
        if (topic == null) {
            return new CampaignSegmentDTO(null, null, 0L, false, MIN_SEGMENT_CAMPAIGNS,
                    new CampaignSegmentMetricDTO(rate, null, null, null),
                    new CampaignSegmentMetricDTO(budget, null, null, null),
                    null, null);
        }
        CampaignSegmentStats stats = getterCampaign.getSegmentStats(topic.getCode(), campaign.getPublicId(),
                rate != null ? rate : 0L, budget != null ? budget : 0L);
        boolean comparable = stats.campaigns() >= MIN_SEGMENT_CAMPAIGNS;
        Long marketRate = topic.getAverageRatePerThousandKopecks();
        return new CampaignSegmentDTO(
                topic.getCode(),
                topic.getName(),
                stats.campaigns(),
                comparable,
                MIN_SEGMENT_CAMPAIGNS,
                metric(rate, stats.medianRatePerThousandKopecks(), stats.lowerRate(), stats.campaigns(), comparable),
                metric(budget, stats.medianBudgetKopecks(), stats.lowerBudget(), stats.campaigns(), comparable),
                marketRate,
                diffPercent(rate, marketRate));
    }

    private static CampaignSegmentMetricDTO metric(Long value, Long median, long lower, long campaigns,
                                                   boolean comparable) {
        if (!comparable) {
            return new CampaignSegmentMetricDTO(value, null, null, null);
        }
        return new CampaignSegmentMetricDTO(value, median, diffPercent(value, median),
                value != null ? (int) Math.round(lower * 100.0 / campaigns) : null);
    }

    /** Округляем как фронт в мастере: 180 против 150 — «+20%», 120 против 150 — «−20%». */
    static Integer diffPercent(Long value, Long base) {
        if (value == null || base == null || base <= 0) {
            return null;
        }
        return (int) Math.round((value - base) * 100.0 / base);
    }

    private static Long positiveOrNull(Long value) {
        return value != null && value > 0 ? value : null;
    }
}
