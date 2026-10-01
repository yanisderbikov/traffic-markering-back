package ru.trafficmarkering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "campaign")
@Getter
@Setter
public class CampaignProperties {

    private long minBudgetRub = 10_000;

    public long minBudgetKopecks() {
        return minBudgetRub * 100;
    }
}
