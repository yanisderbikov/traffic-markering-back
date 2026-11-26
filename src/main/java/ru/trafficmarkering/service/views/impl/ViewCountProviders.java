package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.service.social.SocialIntegration;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.util.List;
import java.util.Optional;

/**
 * Счётчик просмотров для площадки. Флаг social.scrape.enabled выбирает между официальными
 * API площадок и ScrapeCreators: провайдеры другого режима не используются вовсе.
 */
@Component
@RequiredArgsConstructor
class ViewCountProviders {

    private final List<ViewCountProvider> providers;
    private final SocialIntegration socialIntegration;

    Optional<ViewCountProvider> forPlatform(Platform platform) {
        return providers.stream()
                .filter(provider -> provider.platform() == platform && provider.isConfigured())
                .filter(provider -> (provider.source() == ViewSource.SCRAPE_CREATORS) == socialIntegration.scrape())
                .findFirst();
    }
}
