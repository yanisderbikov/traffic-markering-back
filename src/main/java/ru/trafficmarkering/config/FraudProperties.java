package ru.trafficmarkering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Пороги антифрода и репутации. Все значения читаются из application.properties
 * (префикс fraud.*), дефолты подобраны под вертикальные ролики: органика на них
 * даёт 3–10 % лайков к просмотрам, боты — доли процента.
 */
@Configuration
@ConfigurationProperties(prefix = "fraud")
@Getter
@Setter
public class FraudProperties {

    /** С какого балла отклик считается подозрительным: зачисление замораживается */
    private int suspiciousThreshold = 30;

    /** С какого балла отклик считается накруткой: начисление обнуляется до решения админа */
    private int fraudThreshold = 70;

    /** Ниже этого числа просмотров пропорции (лайки, удержание, охват) не считаем: шум */
    private long minViewsForRatios = 5_000;

    /** Потолок оплачиваемых просмотров на ролик для новичков и ограниченных криаторов */
    private long newCreatorMaxPayableViews = 50_000;

    /** Сколько чистых оплаченных роликов переводят новичка в проверенные */
    private int trustedAfterCleanVideos = 3;

    /** Сколько подтверждённых накруток блокируют криатора (одна — ограничивает) */
    private int strikesToBlock = 2;
}
