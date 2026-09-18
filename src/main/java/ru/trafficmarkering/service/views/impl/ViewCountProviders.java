package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class ViewCountProviders {

    private final List<ViewCountProvider> providers;

    Optional<ViewCountProvider> forPlatform(Platform platform) {
        return providers.stream()
                .filter(provider -> provider.platform() == platform && provider.isConfigured())
                .findFirst();
    }
}
