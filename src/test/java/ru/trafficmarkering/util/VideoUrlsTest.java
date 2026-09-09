package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VideoUrlsTest {

    @Test
    void youtubeVideoId_shorts() {
        assertEquals("dQw4w9WgXcQ", VideoUrls.youtubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test
    void youtubeVideoId_watchWithExtraParams() {
        assertEquals("dQw4w9WgXcQ",
                VideoUrls.youtubeVideoId("https://www.youtube.com/watch?t=10&v=dQw4w9WgXcQ&feature=share"));
    }

    @Test
    void youtubeVideoId_shortHost() {
        assertEquals("dQw4w9WgXcQ", VideoUrls.youtubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"));
    }

    @Test
    void youtubeVideoId_unknownLink() {
        assertNull(VideoUrls.youtubeVideoId("https://www.tiktok.com/@user/video/7300000000000000000"));
        assertNull(VideoUrls.youtubeVideoId(null));
    }

    @Test
    void tiktokVideoId_fromProfileLink() {
        assertEquals("7300000000000000000",
                VideoUrls.tiktokVideoId("https://www.tiktok.com/@creator/video/7300000000000000000?is_from_webapp=1"));
    }

    @Test
    void tiktokVideoId_shortLinkNotResolvable() {
        assertNull(VideoUrls.tiktokVideoId("https://vm.tiktok.com/ZMabcdefg/"));
    }

    @Test
    void canonical_dropsQueryWwwAndTrailingSlash() {
        assertEquals("instagram.com/reel/ABC123",
                VideoUrls.canonical("https://www.instagram.com/reel/ABC123/?igsh=xyz"));
        assertEquals("instagram.com/reel/ABC123",
                VideoUrls.canonical("https://instagram.com/reel/ABC123"));
    }

    @Test
    void canonical_handlesGarbage() {
        assertNull(VideoUrls.canonical(null));
        assertNull(VideoUrls.canonical("   "));
    }

    @Test
    void videoKey_sameYoutubeVideoInDifferentLinkForms() {
        String fromShorts = VideoUrls.videoKey(Platform.YOUTUBE_SHORTS,
                "https://www.youtube.com/shorts/dQw4w9WgXcQ");
        String fromWatch = VideoUrls.videoKey(Platform.YOUTUBE_SHORTS,
                "https://youtube.com/watch?v=dQw4w9WgXcQ&feature=share");
        String fromShortHost = VideoUrls.videoKey(Platform.YOUTUBE_SHORTS,
                "https://youtu.be/dQw4w9WgXcQ?si=abc");
        assertEquals("YOUTUBE_SHORTS:dQw4w9WgXcQ", fromShorts);
        assertEquals(fromShorts, fromWatch);
        assertEquals(fromShorts, fromShortHost);
    }

    @Test
    void videoKey_sameTiktokVideoWithDifferentTracking() {
        String plain = VideoUrls.videoKey(Platform.TIKTOK,
                "https://www.tiktok.com/@creator/video/7300000000000000000");
        String tracked = VideoUrls.videoKey(Platform.TIKTOK,
                "https://www.tiktok.com/@creator/video/7300000000000000000?is_from_webapp=1&sender_device=pc");
        assertEquals("TIKTOK:7300000000000000000", plain);
        assertEquals(plain, tracked);
    }

    @Test
    void videoKey_sameInstagramReelWithDifferentDecoration() {
        String plain = VideoUrls.videoKey(Platform.INSTAGRAM, "https://instagram.com/reel/ABC123");
        String decorated = VideoUrls.videoKey(Platform.INSTAGRAM,
                "https://www.instagram.com/reel/ABC123/?igsh=MzRlODBiNWFlZA==");
        assertEquals("INSTAGRAM:instagram.com/reel/ABC123", plain);
        assertEquals(plain, decorated);
    }

    @Test
    void videoKey_differentVideosDoNotCollide() {
        assertNotEquals(
                VideoUrls.videoKey(Platform.TIKTOK, "https://www.tiktok.com/@a/video/7300000000000000000"),
                VideoUrls.videoKey(Platform.TIKTOK, "https://www.tiktok.com/@a/video/7300000000000000001"));
    }

    @Test
    void videoKey_samePathOnDifferentPlatformsDoesNotCollide() {
        assertNotEquals(
                VideoUrls.videoKey(Platform.INSTAGRAM, "https://instagram.com/reel/ABC123"),
                VideoUrls.videoKey(Platform.TELEGRAM, "https://instagram.com/reel/ABC123"));
    }
}
