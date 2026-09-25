package ru.trafficmarkering.service.campaign.impl;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.dto.campaign.CampaignBenchmarkDTO;
import ru.trafficmarkering.repository.GetterCampaign;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampaignBenchmarkServiceImplTest {

    private final GetterCampaign getterCampaign = mock(GetterCampaign.class);
    private final CampaignBenchmarkServiceImpl service = new CampaignBenchmarkServiceImpl(getterCampaign);

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
    void fallsBackPerValue() {
        when(getterCampaign.getMedianRatePerThousandKopecks()).thenReturn(Optional.of(90_00L));
        when(getterCampaign.getMedianBudgetKopecks()).thenReturn(Optional.empty());

        CampaignBenchmarkDTO benchmarks = service.getBenchmarks();

        assertThat(benchmarks.medianRatePerThousandKopecks()).isEqualTo(90_00L);
        assertThat(benchmarks.medianBudgetKopecks()).isEqualTo(100_000_00L);
    }
}
