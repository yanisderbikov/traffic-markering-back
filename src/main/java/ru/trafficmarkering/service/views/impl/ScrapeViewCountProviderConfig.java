package ru.trafficmarkering.service.views.impl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.views.ViewCountProvider;

/** По провайдеру ScrapeCreators на площадку. Включаются флагом в {@link ViewCountProviders}. */
@Configuration
class ScrapeViewCountProviderConfig {

    @Bean
    ViewCountProvider scrapeTiktokViewCountProvider(ScrapeCreatorsApi api, GetterSocialAccount accounts) {
        return new ScrapeViewCountProvider(Platform.TIKTOK, api, accounts);
    }

    @Bean
    ViewCountProvider scrapeInstagramViewCountProvider(ScrapeCreatorsApi api, GetterSocialAccount accounts) {
        return new ScrapeViewCountProvider(Platform.INSTAGRAM, api, accounts);
    }

    @Bean
    ViewCountProvider scrapeYoutubeViewCountProvider(ScrapeCreatorsApi api, GetterSocialAccount accounts) {
        return new ScrapeViewCountProvider(Platform.YOUTUBE_SHORTS, api, accounts);
    }
}
