package ru.trafficmarkering.service.campaign.impl;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CampaignProperties;
import ru.trafficmarkering.dto.campaign.CampaignBenchmarkDTO;
import ru.trafficmarkering.dto.campaign.CampaignSegmentDTO;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignTopic;
import ru.trafficmarkering.repository.CampaignSegmentStats;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.service.campaign.CampaignTopicService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignBenchmarkServiceImplTest {

    private final GetterCampaign getterCampaign = mock(GetterCampaign.class);
    private final CampaignTopicService campaignTopicService = mock(CampaignTopicService.class);
    private final CampaignProperties campaignProperties = new CampaignProperties();
    private final CampaignBenchmarkServiceImpl service =
            new CampaignBenchmarkServiceImpl(getterCampaign, campaignTopicService, campaignProperties);

    @Test
    void returnsMediansFromLaunchedCampaigns() {
        when(getterCampaign.getMedianRatePerThousandKopecks()).thenReturn(Optional.of(220_00L));
        when(getterCampaign.getMedianBudgetKopecks()).thenReturn(Optional.of(45_000_00L));

        CampaignBenchmarkDTO benchmarks = service.getBenchmarks();

        assertThat(benchmarks.medianRatePerThousandKopecks()).isEqualTo(220_00L);
        assertThat(benchmarks.medianBudgetKopecks()).isEqualTo(45_000_00L);
    }

    @Test
    void fallsBackToDefaultsWhenNoCampaignsLaunched() {
        when(getterCampaign.getMedianRatePerThousandKopecks()).thenReturn(Optional.empty());
        when(getterCampaign.getMedianBudgetKopecks()).thenReturn(Optional.empty());

        CampaignBenchmarkDTO benchmarks = service.getBenchmarks();

        assertThat(benchmarks.medianRatePerThousandKopecks()).isEqualTo(150_00L);
        assertThat(benchmarks.medianBudgetKopecks()).isEqualTo(100_000_00L);
    }

    @Test
    void returnsConfiguredMinBudgetInKopecks() {
        campaignProperties.setMinBudgetRub(15_000);
        when(getterCampaign.getMedianRatePerThousandKopecks()).thenReturn(Optional.empty());
        when(getterCampaign.getMedianBudgetKopecks()).thenReturn(Optional.empty());

        assertThat(service.getBenchmarks().minBudgetKopecks()).isEqualTo(15_000_00L);
    }

    @Test
    void fallsBackPerValue() {
        when(getterCampaign.getMedianRatePerThousandKopecks()).thenReturn(Optional.of(90_00L));
        when(getterCampaign.getMedianBudgetKopecks()).thenReturn(Optional.empty());

        CampaignBenchmarkDTO benchmarks = service.getBenchmarks();

        assertThat(benchmarks.medianRatePerThousandKopecks()).isEqualTo(90_00L);
        assertThat(benchmarks.medianBudgetKopecks()).isEqualTo(100_000_00L);
    }

    @Test
    void comparesRateAndBudgetWithinTopic() {
        givenCampaign(topic(200_00L), 240_00L, 50_000_00L);
        when(getterCampaign.getSegmentStats("TECH", "PUB1", 240_00L, 50_000_00L))
                .thenReturn(new CampaignSegmentStats(8, 200_00L, 100_000_00L, 6, 1));

        CampaignSegmentDTO segment = service.getSegment("PUB1");

        assertThat(segment.topic()).isEqualTo("TECH");
        assertThat(segment.topicDescription()).isEqualTo("Технологии и гаджеты");
        assertThat(segment.campaignsCount()).isEqualTo(8);
        assertThat(segment.comparable()).isTrue();
        assertThat(segment.rate().kopecks()).isEqualTo(240_00L);
        assertThat(segment.rate().medianKopecks()).isEqualTo(200_00L);
        assertThat(segment.rate().diffPercent()).isEqualTo(20);
        assertThat(segment.rate().higherThanPercent()).isEqualTo(75);
        assertThat(segment.budget().medianKopecks()).isEqualTo(100_000_00L);
        assertThat(segment.budget().diffPercent()).isEqualTo(-50);
        assertThat(segment.budget().higherThanPercent()).isEqualTo(13);
        assertThat(segment.marketRatePerThousandKopecks()).isEqualTo(200_00L);
        assertThat(segment.marketRateDiffPercent()).isEqualTo(20);
    }

    @Test
    void hidesMediansWhileTopicHasTooFewCampaigns() {
        givenCampaign(topic(200_00L), 180_00L, 50_000_00L);
        when(getterCampaign.getSegmentStats(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new CampaignSegmentStats(2, 300_00L, 80_000_00L, 0, 1));

        CampaignSegmentDTO segment = service.getSegment("PUB1");

        assertThat(segment.campaignsCount()).isEqualTo(2);
        assertThat(segment.comparable()).isFalse();
        assertThat(segment.minCampaignsCount()).isEqualTo(3);
        assertThat(segment.rate().kopecks()).isEqualTo(180_00L);
        assertThat(segment.rate().medianKopecks()).isNull();
        assertThat(segment.rate().diffPercent()).isNull();
        assertThat(segment.rate().higherThanPercent()).isNull();
        assertThat(segment.budget().medianKopecks()).isNull();
        assertThat(segment.marketRateDiffPercent()).isEqualTo(-10);
    }

    @Test
    void comparesNothingWithoutTopic() {
        givenCampaign(null, 180_00L, 50_000_00L);

        CampaignSegmentDTO segment = service.getSegment("PUB1");

        assertThat(segment.topic()).isNull();
        assertThat(segment.comparable()).isFalse();
        assertThat(segment.rate().kopecks()).isEqualTo(180_00L);
        assertThat(segment.marketRatePerThousandKopecks()).isNull();
        verify(getterCampaign, never()).getSegmentStats(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void leavesUnfilledValuesUncompared() {
        givenCampaign(topic(null), null, 50_000_00L);
        when(getterCampaign.getSegmentStats("TECH", "PUB1", 0L, 50_000_00L))
                .thenReturn(new CampaignSegmentStats(5, 200_00L, 100_000_00L, 0, 2));

        CampaignSegmentDTO segment = service.getSegment("PUB1");

        assertThat(segment.rate().kopecks()).isNull();
        assertThat(segment.rate().medianKopecks()).isEqualTo(200_00L);
        assertThat(segment.rate().diffPercent()).isNull();
        assertThat(segment.rate().higherThanPercent()).isNull();
        assertThat(segment.budget().higherThanPercent()).isEqualTo(40);
        assertThat(segment.marketRatePerThousandKopecks()).isNull();
        assertThat(segment.marketRateDiffPercent()).isNull();
    }

    @Test
    void segmentOfUnknownCampaignIsNotFound() {
        when(getterCampaign.getByPublicId("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSegment("NOPE")).isInstanceOf(ResponseStatusException.class);
    }

    private void givenCampaign(CampaignTopic topic, Long rate, Long budget) {
        Campaign campaign = Campaign.builder()
                .publicId("PUB1")
                .topic(topic)
                .ratePerThousandKopecks(rate)
                .budgetKopecks(budget)
                .build();
        when(getterCampaign.getByPublicId("PUB1")).thenReturn(Optional.of(campaign));
    }

    private static CampaignTopic topic(Long averageRate) {
        return CampaignTopic.builder()
                .code("TECH")
                .name("Технологии и гаджеты")
                .normalizedName("технологии и гаджеты")
                .averageRatePerThousandKopecks(averageRate)
                .build();
    }
}
