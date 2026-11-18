package ru.trafficmarkering.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Configuration
@ConfigurationProperties(prefix = "commission")
@Getter
@Setter
public class CommissionProperties {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BigDecimal percent = BigDecimal.TEN;

    private BigDecimal partnerSharePercent = BigDecimal.TEN;

    @PostConstruct
    void validate() {
        requirePercent("commission.percent", percent);
        requirePercent("commission.partner-share-percent", partnerSharePercent);
    }

    public long commissionOf(long amountKopecks) {
        return share(amountKopecks, percent, RoundingMode.HALF_UP);
    }

    public long partnerShareOf(long commissionKopecks) {
        return share(commissionKopecks, partnerSharePercent, RoundingMode.DOWN);
    }

    private static long share(long amountKopecks, BigDecimal sharePercent, RoundingMode rounding) {
        if (amountKopecks <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(amountKopecks)
                .multiply(sharePercent)
                .divide(HUNDRED, 0, rounding)
                .longValueExact();
    }

    private static void requirePercent(String name, BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) >= 0) {
            throw new IllegalStateException(name + " должен быть от 0 до 100 (не включая 100), сейчас " + value);
        }
    }
}
