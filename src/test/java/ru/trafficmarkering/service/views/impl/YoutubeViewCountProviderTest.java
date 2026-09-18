package ru.trafficmarkering.service.views.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.social.SocialTokenService;
import ru.trafficmarkering.service.views.ViewCount;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YoutubeViewCountProviderTest {

    private static final Long CREATOR_ID = 7L;
    private static final String VIDEO_ID = "abc123xyz";
    private static final String VIDEO_URL = "https://www.youtube.com/shorts/" + VIDEO_ID;
    private static final String DATA_API = "https://www.googleapis.com/youtube/v3/videos";
    private static final String ANALYTICS_API = "https://youtubeanalytics.googleapis.com/v2/reports";
    private static final String DATA_SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
    private static final String TOKEN = "creator-oauth-token";

    private final JsonHttpClient httpClient = mock(JsonHttpClient.class);
    private final SocialTokenService socialTokenService = mock(SocialTokenService.class);
    private final GetterSocialAccount getterSocialAccount = mock(GetterSocialAccount.class);

    private final YoutubeViewCountProvider provider =
            new YoutubeViewCountProvider(httpClient, socialTokenService, getterSocialAccount);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(provider, "apiKey", "data-api-key");
        Map<String, Object> dataApiResponse = Map.of("items", List.of(
                Map.of("id", VIDEO_ID, "statistics", Map.of("viewCount", "1000"))));
        when(httpClient.getJson(startsWith(DATA_API), isNull(), eq("YouTube"))).thenReturn(dataApiResponse);
    }

    @Test
    void fetchViews_withoutLinkedAccount_returnsTotalWithUnknownGeography() {
        when(getterSocialAccount.getActiveByUserIdAndPlatform(CREATOR_ID, Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.empty());

        Map<String, ViewCount> result = provider.fetchViews(CREATOR_ID, List.of(VIDEO_URL));

        ViewCount count = result.get(VIDEO_URL);
        assertEquals(1000L, count.total());
        assertFalse(count.geographyKnown());
        verify(httpClient, times(1)).getJson(any(), any(), any());
        verify(socialTokenService, never()).accessToken(any(), any());
    }

    @Test
    void fetchViews_withoutAnalyticsScope_returnsTotalWithUnknownGeography() {
        when(getterSocialAccount.getActiveByUserIdAndPlatform(CREATOR_ID, Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.of(account(DATA_SCOPE)));

        Map<String, ViewCount> result = provider.fetchViews(CREATOR_ID, List.of(VIDEO_URL));

        ViewCount count = result.get(VIDEO_URL);
        assertEquals(1000L, count.total());
        assertFalse(count.geographyKnown());
        verify(httpClient, times(1)).getJson(any(), any(), any());
        verify(socialTokenService, never()).accessToken(any(), any());
    }

    @Test
    void fetchViews_withAnalyticsScope_attachesCountryBreakdown() {
        linkAccountWithAnalytics();
        Map<String, Object> analyticsResponse = Map.of(
                "columnHeaders", List.of(Map.of("name", "country"), Map.of("name", "views")),
                "rows", List.of(List.of("RU", 700), List.of("ZZ", 5), List.of("KZ"), "garbage"));
        when(httpClient.getJson(startsWith(ANALYTICS_API), eq(TOKEN), eq("YouTube")))
                .thenReturn(analyticsResponse);

        Map<String, ViewCount> result = provider.fetchViews(CREATOR_ID, List.of(VIDEO_URL));

        ViewCount count = result.get(VIDEO_URL);
        assertEquals(1000L, count.total());
        assertTrue(count.geographyKnown());
        assertEquals(Map.of("RU", 700L, "ZZ", 5L), count.byCountry());

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(2)).getJson(urls.capture(), any(), any());
        String analyticsUrl = urls.getAllValues().get(1);
        assertTrue(analyticsUrl.contains("youtubeanalytics.googleapis.com"));
        assertTrue(analyticsUrl.contains("ids=channel%3D%3DMINE"));
        assertTrue(analyticsUrl.contains("dimensions=country"));
        assertTrue(analyticsUrl.contains("filters=video%3D%3D" + VIDEO_ID));
    }

    @Test
    void fetchViews_whenAnalyticsFails_keepsTotalWithUnknownGeography() {
        linkAccountWithAnalytics();
        when(httpClient.getJson(startsWith(ANALYTICS_API), eq(TOKEN), eq("YouTube")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "YouTube не отдал данные"));

        Map<String, ViewCount> result = assertDoesNotThrow(
                () -> provider.fetchViews(CREATOR_ID, List.of(VIDEO_URL)));

        ViewCount count = result.get(VIDEO_URL);
        assertEquals(1000L, count.total());
        assertFalse(count.geographyKnown());
    }

    @Test
    void fetchViews_whenAnalyticsHasNoRows_reportsKnownEmptyGeography() {
        linkAccountWithAnalytics();
        Map<String, Object> analyticsResponse = Map.of(
                "columnHeaders", List.of(Map.of("name", "country"), Map.of("name", "views")));
        when(httpClient.getJson(startsWith(ANALYTICS_API), eq(TOKEN), eq("YouTube")))
                .thenReturn(analyticsResponse);

        Map<String, ViewCount> result = provider.fetchViews(CREATOR_ID, List.of(VIDEO_URL));

        ViewCount count = result.get(VIDEO_URL);
        assertEquals(1000L, count.total());
        assertTrue(count.geographyKnown());
        assertTrue(count.byCountry().isEmpty());
    }

    private void linkAccountWithAnalytics() {
        when(getterSocialAccount.getActiveByUserIdAndPlatform(CREATOR_ID, Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.of(account(DATA_SCOPE + " " + SocialAccount.YOUTUBE_ANALYTICS_SCOPE)));
        when(socialTokenService.accessToken(CREATOR_ID, Platform.YOUTUBE_SHORTS))
                .thenReturn(Optional.of(TOKEN));
    }

    private static SocialAccount account(String scopes) {
        return SocialAccount.builder()
                .platform(Platform.YOUTUBE_SHORTS)
                .scopes(scopes)
                .build();
    }
}
