package ru.trafficmarkering.service.views.impl;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeVideo;
import ru.trafficmarkering.service.social.SocialIntegration;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScrapeViewCountProviderTest {

    private static final String OWN = "https://www.tiktok.com/@creator/video/1111111111";
    private static final String FOREIGN = "https://www.tiktok.com/@famous/video/2222222222";
    private static final String MISSING = "https://www.tiktok.com/@creator/video/3333333333";

    private final ScrapeCreatorsApi api = mock(ScrapeCreatorsApi.class);
    private final GetterSocialAccount getterSocialAccount = mock(GetterSocialAccount.class);
    private final ScrapeViewCountProvider provider =
            new ScrapeViewCountProvider(Platform.TIKTOK, api, getterSocialAccount);

    @Test
    void countsOnlyVideosOfLinkedAccounts() {
        when(getterSocialAccount.getAllActiveByUserIdAndPlatform(7L, Platform.TIKTOK)).thenReturn(List.of(
                SocialAccount.builder().externalId("tt-1").username("creator").build()));
        when(api.video(Platform.TIKTOK, OWN)).thenReturn(new ScrapeVideo("tt-1", "creator",
                5000L, 300L, 12L, 4L, 9L, Instant.parse("2026-11-01T10:00:00Z")));
        when(api.video(Platform.TIKTOK, FOREIGN)).thenReturn(new ScrapeVideo("tt-9", "famous",
                9_000_000L, 1L, 1L, 1L, 1L, null));
        when(api.video(Platform.TIKTOK, MISSING)).thenThrow(new ScrapeCreatorsException(404, "not_found"));

        Map<String, ViewCount> views = provider.fetchViews(7L, List.of(OWN, FOREIGN, MISSING));

        assertThat(views).containsOnlyKeys(OWN);
        ViewCount own = views.get(OWN);
        assertThat(own.total()).isEqualTo(5000L);
        assertThat(own.geographyKnown()).isFalse();
        assertThat(own.metrics().likes()).isEqualTo(300L);
        assertThat(own.metrics().saves()).isEqualTo(9L);
        assertThat(own.metrics().publishedAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
    }

    @Test
    void withoutLinkedAccountDoesNotSpendCredits() {
        when(getterSocialAccount.getAllActiveByUserIdAndPlatform(7L, Platform.TIKTOK)).thenReturn(List.of());

        assertThat(provider.fetchViews(7L, List.of(OWN))).isEmpty();
        verify(api, never()).video(any(), any());
    }

    @Test
    void outOfCreditsStopsTheRun() {
        when(getterSocialAccount.getAllActiveByUserIdAndPlatform(7L, Platform.TIKTOK)).thenReturn(List.of(
                SocialAccount.builder().externalId("tt-1").build()));
        when(api.video(Platform.TIKTOK, OWN)).thenThrow(new ScrapeCreatorsException(402, "no credits"));

        assertThatThrownBy(() -> provider.fetchViews(7L, List.of(OWN, MISSING)))
                .isInstanceOf(ScrapeCreatorsException.class);
        verify(api, never()).video(Platform.TIKTOK, MISSING);
    }

    @Test
    void flagPicksProviderFamily() {
        ViewCountProvider official = mock(ViewCountProvider.class);
        when(official.platform()).thenReturn(Platform.TIKTOK);
        when(official.source()).thenReturn(ViewSource.TIKTOK_API);
        when(official.isConfigured()).thenReturn(true);
        when(api.isConfigured()).thenReturn(true);

        assertThat(new ViewCountProviders(List.of(official, provider), new SocialIntegration(false))
                .forPlatform(Platform.TIKTOK)).contains(official);
        assertThat(new ViewCountProviders(List.of(official, provider), new SocialIntegration(true))
                .forPlatform(Platform.TIKTOK)).contains(provider);
    }
}
