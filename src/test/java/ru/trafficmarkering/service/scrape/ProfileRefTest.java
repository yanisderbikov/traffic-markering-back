package ru.trafficmarkering.service.scrape;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Platform;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileRefTest {

    @Test
    void tiktok_acceptsHandleWithOrWithoutAtAndProfileLink() {
        assertThat(ProfileRef.parse(Platform.TIKTOK, "@stool.presidente"))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "stool.presidente"));
        assertThat(ProfileRef.parse(Platform.TIKTOK, " stoolpresidente "))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "stoolpresidente"));
        assertThat(ProfileRef.parse(Platform.TIKTOK, "https://www.tiktok.com/@stoolpresidente?lang=ru"))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "stoolpresidente"));
        assertThat(ProfileRef.parse(Platform.TIKTOK, "tiktok.com/@stoolpresidente/video/7517114944362499342"))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "stoolpresidente"));
    }

    @Test
    void instagram_takesFirstPathSegmentButNotPostLinks() {
        assertThat(ProfileRef.parse(Platform.INSTAGRAM, "https://instagram.com/adrianhorning/"))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "adrianhorning"));
        assertThat(ProfileRef.parse(Platform.INSTAGRAM, "www.instagram.com/adrian_horning"))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "adrian_horning"));
        assertThat(ProfileRef.parse(Platform.INSTAGRAM, "https://www.instagram.com/reel/DLDXI0fylTC/")).isEmpty();
    }

    @Test
    void youtube_supportsHandleChannelIdAndLegacyLinks() {
        assertThat(ProfileRef.parse(Platform.YOUTUBE_SHORTS, "https://www.youtube.com/@ThePatMcAfeeShow/shorts"))
                .contains(new ProfileRef(ProfileRef.Kind.HANDLE, "ThePatMcAfeeShow"));
        assertThat(ProfileRef.parse(Platform.YOUTUBE_SHORTS, "UCxcTeAKWJca6XyJ37_ZoKIQ"))
                .contains(new ProfileRef(ProfileRef.Kind.CHANNEL_ID, "UCxcTeAKWJca6XyJ37_ZoKIQ"));
        assertThat(ProfileRef.parse(Platform.YOUTUBE_SHORTS, "https://youtube.com/channel/UCxcTeAKWJca6XyJ37_ZoKIQ"))
                .contains(new ProfileRef(ProfileRef.Kind.CHANNEL_ID, "UCxcTeAKWJca6XyJ37_ZoKIQ"));
        assertThat(ProfileRef.parse(Platform.YOUTUBE_SHORTS, "https://www.youtube.com/c/PatMcAfee"))
                .contains(new ProfileRef(ProfileRef.Kind.URL, "https://www.youtube.com/c/PatMcAfee"));
    }

    @Test
    void rejectsForeignLinksAndGarbage() {
        assertThat(ProfileRef.parse(Platform.TIKTOK, "https://instagram.com/someone")).isEmpty();
        assertThat(ProfileRef.parse(Platform.TIKTOK, "ник с пробелами")).isEmpty();
        assertThat(ProfileRef.parse(Platform.INSTAGRAM, "")).isEmpty();
        assertThat(ProfileRef.parse(Platform.TELEGRAM, "@channel")).isEqualTo(Optional.empty());
    }
}
