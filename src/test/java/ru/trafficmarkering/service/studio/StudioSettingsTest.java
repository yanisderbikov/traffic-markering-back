package ru.trafficmarkering.service.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudioSettingsTest {

    @Test
    void unchangedExportIsRejectedUnlessAllowed() {
        assertThatThrownBy(() -> new StudioSettings().validate(false, false, false))
                .isInstanceOf(StudioException.class)
                .hasMessageContaining("Измените исходник");
        new StudioSettings().validate(false, true, false);
        new StudioSettings().validate(false, false, true);
    }

    @Test
    void splitScreenNeedsSecondVideo() {
        StudioSettings settings = new StudioSettings();
        settings.setSplitPlace("top");
        assertThatThrownBy(() -> settings.validate(false, false, true)).hasMessageContaining("второе видео");
        settings.setBackground("file");
        settings.validate(false, false, true);
    }

    @Test
    void pauseDurationLimitedForUsersOnly() {
        StudioSettings settings = new StudioSettings();
        settings.setMode("pause");
        settings.setDuration(45.0);
        assertThatThrownBy(() -> settings.validate(false, false, true)).hasMessageContaining("параметры");
        settings.validate(true, false, true);
    }

    @Test
    void stripSizeAndPlaceAreChecked() {
        StudioSettings settings = new StudioSettings();
        settings.setMode("strip");
        settings.setPlace("center");
        assertThatThrownBy(() -> settings.validate(false, false, true)).isInstanceOf(StudioException.class);
        settings.setPlace("top");
        settings.setSize(50.0);
        assertThatThrownBy(() -> settings.validate(false, false, true)).isInstanceOf(StudioException.class);
        settings.setSize(30.0);
        settings.validate(false, false, true);
        assertThat(settings.sizeEffective()).isEqualTo(30.0);
    }

    @Test
    void rejectsUnknownPresetAndColor() {
        StudioSettings settings = new StudioSettings();
        settings.setBackground("preset");
        settings.setPreset("rainbow");
        assertThatThrownBy(() -> settings.validate(false, false, true)).isInstanceOf(StudioException.class);
        settings.setPreset("ocean");
        settings.setColor("red");
        assertThatThrownBy(() -> settings.validate(false, false, true)).isInstanceOf(StudioException.class);
    }

    @Test
    void trimmedFragmentMustKeepHalfSecond() {
        StudioSettings settings = new StudioSettings();
        settings.setSourceStart(3);
        settings.setSourceEnd(3.2);
        assertThatThrownBy(() -> settings.validate(false, false, true)).hasMessageContaining("0,5 секунды");
    }

    @Test
    void jsonRoundTripKeepsDefaultsAndIgnoresUnknownFields() throws Exception {
        ObjectMapper json = new ObjectMapper();
        StudioSettings settings = json.readValue("{\"format\":\"vertical\",\"legacy\":1}", StudioSettings.class);
        assertThat(settings.getFormat()).isEqualTo("vertical");
        assertThat(settings.getQuality()).isEqualTo("1080");
        assertThat(settings.bannerAudioEffective()).isFalse();
        StudioSettings copy = json.readValue(json.writeValueAsString(settings), StudioSettings.class);
        assertThat(copy).isEqualTo(settings);
        assertThat(settings.copy()).isEqualTo(settings);
    }
}
