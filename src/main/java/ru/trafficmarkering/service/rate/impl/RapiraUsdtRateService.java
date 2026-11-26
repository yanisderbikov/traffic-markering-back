package ru.trafficmarkering.service.rate.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.rate.UsdtRateDTO;
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.http.JsonNode;
import ru.trafficmarkering.service.rate.UsdtRateService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
class RapiraUsdtRateService implements UsdtRateService {

    private static final String NAME = "Rapira";
    private static final String RATES_URL = "https://api.rapira.net/open/market/rates";
    private static final String SYMBOL = "USDT/RUB";
    private static final Duration TTL = Duration.ofMinutes(1);

    private final JsonHttpClient httpClient;

    private volatile UsdtRateDTO cached;
    private volatile Instant cachedAt;

    @Override
    public UsdtRateDTO current() {
        Instant now = Instant.now();
        UsdtRateDTO rate = cached;
        if (rate != null && cachedAt.plus(TTL).isAfter(now)) {
            return rate;
        }
        synchronized (this) {
            if (cached != null && cachedAt.plus(TTL).isAfter(now)) {
                return cached;
            }
            try {
                cached = fetch(now);
                cachedAt = now;
            } catch (ResponseStatusException e) {
                if (cached == null) {
                    throw e;
                }
                log.warn("Rapira не отдала курс, отдаём прошлый от {}: {}", cached.fetchedAt(), e.getReason());
            }
            return cached;
        }
    }

    private UsdtRateDTO fetch(Instant now) {
        Map<String, Object> body = httpClient.getJson(RATES_URL, null, NAME);
        Map<String, Object> pair = JsonNode.array(body, "data").stream()
                .filter(Map.class::isInstance)
                .map(this::asObject)
                .filter(item -> SYMBOL.equals(JsonNode.text(item, "symbol")))
                .findFirst()
                .orElseThrow(() -> unavailable("в ответе нет пары " + SYMBOL));
        BigDecimal ask = price(pair, "askPrice");
        BigDecimal bid = price(pair, "bidPrice");
        return new UsdtRateDTO(ask, bid, now.toString(), NAME);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObject(Object value) {
        return (Map<String, Object>) value;
    }

    private BigDecimal price(Map<String, Object> pair, String key) {
        String text = JsonNode.text(pair, key);
        if (text == null) {
            throw unavailable("нет поля " + key);
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.signum() <= 0) {
                throw unavailable("поле " + key + " не положительное: " + text);
            }
            return value;
        } catch (NumberFormatException e) {
            throw unavailable("поле " + key + " не число: " + text);
        }
    }

    private ResponseStatusException unavailable(String detail) {
        log.error("Rapira вернула неожиданный курс: {}", detail);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Курс USDT временно недоступен");
    }
}
