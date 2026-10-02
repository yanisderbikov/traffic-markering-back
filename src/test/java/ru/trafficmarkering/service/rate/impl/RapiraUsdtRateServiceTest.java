package ru.trafficmarkering.service.rate.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.rate.UsdtRateDTO;
import ru.trafficmarkering.service.http.JsonHttpClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RapiraUsdtRateServiceTest {

    private final JsonHttpClient httpClient = mock(JsonHttpClient.class);
    private final RapiraUsdtRateService service = new RapiraUsdtRateService(httpClient);

    @Test
    void picksUsdtRubPairAndCachesIt() {
        when(httpClient.getJson(anyString(), any(), anyString())).thenReturn(Map.of("data", List.of(
                Map.of("symbol", "BTC/USDT", "askPrice", 83608, "bidPrice", 83320.5),
                Map.of("symbol", "USDT/RUB", "askPrice", 86.76, "bidPrice", 86.73))));

        UsdtRateDTO rate = service.current();
        service.current();

        assertThat(rate.askPrice()).isEqualByComparingTo(new BigDecimal("86.76"));
        assertThat(rate.bidPrice()).isEqualByComparingTo(new BigDecimal("86.73"));
        assertThat(rate.source()).isEqualTo("Rapira");
        verify(httpClient, times(1)).getJson(anyString(), any(), anyString());
    }

    @Test
    void failsWhenPairIsMissing() {
        when(httpClient.getJson(anyString(), any(), anyString())).thenReturn(Map.of("data", List.of(
                Map.of("symbol", "BTC/USDT", "askPrice", 83608, "bidPrice", 83320.5))));

        assertThatThrownBy(service::current)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
