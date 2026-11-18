package ru.trafficmarkering.service.scrape;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ответы — урезанные примеры из OpenAPI ScrapeCreators (docs.scrapecreators.com/openapi.json). */
class ScrapeCreatorsApiTest {

    private final ScrapeCreatorsClient client = mock(ScrapeCreatorsClient.class);
    private final ScrapeCreatorsApi api = new ScrapeCreatorsApi(client);

    @Test
    void tiktokProfile() {
        when(client.get("/v1/tiktok/profile", Map.of("handle", "stoolpresidente"))).thenReturn(Map.of(
                "success", true,
                "user", Map.of("id", "6659752019493208069", "uniqueId", "stoolpresidente",
                        "nickname", "Dave Portnoy", "avatarMedium", "https://cdn/720.jpeg",
                        "signature", "El Presidente OFFER-7KQ2PX", "privateAccount", false),
                "stats", Map.of("followerCount", 4100000, "videoCount", 2017)));

        ScrapeProfile profile = api.profile(Platform.TIKTOK, new ProfileRef(ProfileRef.Kind.HANDLE, "stoolpresidente"));

        assertThat(profile).isEqualTo(new ScrapeProfile("6659752019493208069", "stoolpresidente", "Dave Portnoy",
                "https://cdn/720.jpeg", 4100000L, "El Presidente OFFER-7KQ2PX", ScrapeProfile.Privacy.PUBLIC));
    }

    @Test
    void tiktokVideo() {
        String url = "https://www.tiktok.com/@stoolpresidente/video/7517114944362499342";
        when(client.get("/v2/tiktok/video", Map.of("url", url))).thenReturn(Map.of(
                "success", true,
                "aweme_detail", Map.of(
                        "aweme_id", "7517114944362499342",
                        "create_time", 1750214725,
                        "statistics", Map.of("play_count", 589745, "digg_count", 62522, "comment_count", 618,
                                "share_count", 3585, "collect_count", 1633),
                        "author", Map.of("uid", "6659752019493208069", "unique_id", "stoolpresidente"))));

        ScrapeVideo video = api.video(Platform.TIKTOK, url);

        assertThat(video).isEqualTo(new ScrapeVideo("6659752019493208069", "stoolpresidente",
                589745L, 62522L, 618L, 3585L, 1633L, Instant.ofEpochSecond(1750214725)));
    }

    @Test
    void instagramProfileAndPost() {
        when(client.get("/v1/instagram/profile", Map.of("handle", "adrianhorning"))).thenReturn(Map.of(
                "success", true,
                "data", Map.of("user", Map.of("id", "2700692569", "username", "adrianhorning",
                        "full_name", "Adrian Horning", "biography", "Building scrapecreators.com",
                        "edge_followed_by", Map.of("count", 24555), "profile_pic_url_hd", "https://cdn/hd.jpg",
                        "is_private", true))));
        String url = "https://www.instagram.com/reel/DLDXI0fylTC/";
        when(client.get("/v1/instagram/post", Map.of("url", url))).thenReturn(Map.of(
                "success", true,
                "data", Map.of("xdt_shortcode_media", Map.of(
                        "shortcode", "DLDXI0fylTC", "video_view_count", 0, "video_play_count", 425901,
                        "owner", Map.of("id", "21393171", "username", "jane"),
                        "edge_media_preview_like", Map.of("count", 22926),
                        "edge_media_to_parent_comment", Map.of("count", 299),
                        "taken_at_timestamp", 1750272151))));

        ScrapeProfile profile = api.profile(Platform.INSTAGRAM, new ProfileRef(ProfileRef.Kind.HANDLE, "adrianhorning"));
        ScrapeVideo video = api.video(Platform.INSTAGRAM, url);

        assertThat(profile.externalId()).isEqualTo("2700692569");
        assertThat(profile.followers()).isEqualTo(24555L);
        assertThat(profile.avatarUrl()).isEqualTo("https://cdn/hd.jpg");
        assertThat(profile.privacy()).isEqualTo(ScrapeProfile.Privacy.PRIVATE);
        assertThat(video).isEqualTo(new ScrapeVideo("21393171", "jane", 425901L, 22926L, 299L, null, null,
                Instant.ofEpochSecond(1750272151)));
    }

    @Test
    void youtubeChannelAndVideo() {
        when(client.get(eq("/v1/youtube/channel"), eq(Map.of("channelId", "UCxcTeAKWJca6XyJ37_ZoKIQ"))))
                .thenReturn(Map.of(
                        "success", true,
                        "channelId", "UCxcTeAKWJca6XyJ37_ZoKIQ",
                        "channel", "http://www.youtube.com/@ThePatMcAfeeShow",
                        "name", "The Pat McAfee Show",
                        "description", "Код OFFER-7KQ2PX",
                        "subscriberCount", 2750000,
                        "avatar", Map.of("image", Map.of("sources", List.of(
                                Map.of("url", "https://yt3/small", "width", 72),
                                Map.of("url", "https://yt3/large", "width", 900))))));
        String url = "https://www.youtube.com/shorts/Y2Ah_DFr8cw";
        when(client.get("/v1/youtube/video", Map.of("url", url))).thenReturn(Map.of(
                "success", true,
                "id", "Y2Ah_DFr8cw",
                "publishDate", "2019-02-22T03:19:54-08:00",
                "viewCountInt", 372864, "likeCountInt", 4043, "commentCountInt", 358,
                "channel", Map.of("id", "UCWH3hing1Qb4LnkRfQdxsxQ", "handle", "afroballer8906")));

        ScrapeProfile channel = api.profile(Platform.YOUTUBE_SHORTS,
                new ProfileRef(ProfileRef.Kind.CHANNEL_ID, "UCxcTeAKWJca6XyJ37_ZoKIQ"));
        ScrapeVideo video = api.video(Platform.YOUTUBE_SHORTS, url);

        assertThat(channel).isEqualTo(new ScrapeProfile("UCxcTeAKWJca6XyJ37_ZoKIQ", "ThePatMcAfeeShow",
                "The Pat McAfee Show", "https://yt3/large", 2750000L, "Код OFFER-7KQ2PX", ScrapeProfile.Privacy.PUBLIC));
        assertThat(video).isEqualTo(new ScrapeVideo("UCWH3hing1Qb4LnkRfQdxsxQ", "afroballer8906",
                372864L, 4043L, 358L, null, null, Instant.parse("2019-02-22T11:19:54Z")));
    }

    /** Свой ScrapeCreators не отдаёт is_private, когда embed профиля пуст: закрыт профиль или нет — неизвестно. */
    @Test
    void instagramPrivacyIsUnknownWithoutFlag() {
        when(client.get("/v1/instagram/profile", Map.of("handle", "open"))).thenReturn(Map.of(
                "success", true,
                "data", Map.of("user", Map.of("id", "1", "username", "open", "is_private", false))));
        when(client.get("/v1/instagram/profile", Map.of("handle", "hidden"))).thenReturn(Map.of(
                "success", true,
                "data", Map.of("user", Map.of("id", "2", "username", "hidden", "biography", "OFFER-7KQ2PX"))));

        assertThat(api.profile(Platform.INSTAGRAM, new ProfileRef(ProfileRef.Kind.HANDLE, "open")).privacy())
                .isEqualTo(ScrapeProfile.Privacy.PUBLIC);
        assertThat(api.profile(Platform.INSTAGRAM, new ProfileRef(ProfileRef.Kind.HANDLE, "hidden")).privacy())
                .isEqualTo(ScrapeProfile.Privacy.UNKNOWN);
    }

    @Test
    void tiktokPrivacyFromEitherFlag() {
        when(client.get("/v1/tiktok/profile", Map.of("handle", "secret"))).thenReturn(Map.of(
                "success", true,
                "user", Map.of("id", "1", "privateAccount", false, "secret", true)));
        when(client.get("/v1/tiktok/profile", Map.of("handle", "bare"))).thenReturn(Map.of(
                "success", true,
                "user", Map.of("id", "2")));

        assertThat(api.profile(Platform.TIKTOK, new ProfileRef(ProfileRef.Kind.HANDLE, "secret")).privacy())
                .isEqualTo(ScrapeProfile.Privacy.PRIVATE);
        assertThat(api.profile(Platform.TIKTOK, new ProfileRef(ProfileRef.Kind.HANDLE, "bare")).privacy())
                .isEqualTo(ScrapeProfile.Privacy.UNKNOWN);
    }

    @Test
    void emptyProfileIsNotFound() {
        when(client.get("/v1/tiktok/profile", Map.of("handle", "ghost"))).thenReturn(Map.of("success", true));

        assertThatThrownBy(() -> api.profile(Platform.TIKTOK, new ProfileRef(ProfileRef.Kind.HANDLE, "ghost")))
                .isInstanceOfSatisfying(ScrapeCreatorsException.class, e -> assertThat(e.notFound()).isTrue());
    }

    @Test
    void videoOwnership_comparesIdFirstThenUsername() {
        ScrapeVideo withId = new ScrapeVideo("42", "nick", 1L, null, null, null, null, null);
        ScrapeVideo withoutId = new ScrapeVideo(null, "Nick", 1L, null, null, null, null, null);

        assertThat(withId.publishedBy("42", "other")).isTrue();
        assertThat(withId.publishedBy("43", "nick")).isFalse();
        assertThat(withoutId.publishedBy("43", "nick")).isTrue();
        assertThat(withoutId.publishedBy("43", null)).isFalse();
    }
}
