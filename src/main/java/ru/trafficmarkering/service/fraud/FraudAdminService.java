package ru.trafficmarkering.service.fraud;

import ru.trafficmarkering.dto.fraud.CreatorTrustDTO;
import ru.trafficmarkering.dto.fraud.FraudApplicationDTO;
import ru.trafficmarkering.dto.fraud.FraudReviewRequestDTO;
import ru.trafficmarkering.dto.fraud.TrustUpdateRequestDTO;

import java.util.List;
import java.util.UUID;

/** Кабинет антифрода: очередь подозрительных роликов и репутация криаторов. */
public interface FraudAdminService {

    /**
     * @param filter {@code attention} (по умолчанию) — SUSPICIOUS и FRAUD, {@code all} — все
     *               отклики со снятыми замерами, либо имя конкретного статуса
     */
    List<FraudApplicationDTO> applications(String filter);

    FraudApplicationDTO application(UUID id);

    FraudApplicationDTO review(UUID id, FraudReviewRequestDTO request);

    FraudApplicationDTO recheck(UUID id);

    /** Прогнать скоринг по всем откликам в работе; ответ — сколько откликов проверили. */
    int recheckAll();

    List<CreatorTrustDTO> creators();

    CreatorTrustDTO updateTrust(Long creatorId, TrustUpdateRequestDTO request);
}
