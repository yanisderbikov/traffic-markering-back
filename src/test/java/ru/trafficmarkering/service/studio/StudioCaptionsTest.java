package ru.trafficmarkering.service.studio;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudioCaptionsTest {

    @Test
    void parsesSrtWithSequenceNumbersAndTags() {
        List<StudioCaptions.Cue> cues = StudioCaptions.fromText("""
                1
                00:00:01,500 --> 00:00:03,000
                <b>Привет</b>&amp;пока

                2
                00:00:04,000 --> 00:00:05,250
                Вторая<br>строка
                """);
        assertThat(cues).containsExactly(
                new StudioCaptions.Cue(1.5, 3.0, "Привет&пока"),
                new StudioCaptions.Cue(4.0, 5.25, "Вторая\nстрока"));
    }

    @Test
    void parsesVttAndSkipsHeaders() {
        List<StudioCaptions.Cue> cues = StudioCaptions.fromText("""
                WEBVTT

                NOTE комментарий

                00:01.000 --> 00:02.000 align:start
                Фраза
                """);
        assertThat(cues).containsExactly(new StudioCaptions.Cue(1.0, 2.0, "Фраза"));
    }

    @Test
    void plainTextCoversWholeVideo() {
        assertThat(StudioCaptions.fromText("  Подпись на весь ролик ")).containsExactly(
                new StudioCaptions.Cue(0, null, "Подпись на весь ролик"));
        assertThat(StudioCaptions.fromText("  ")).isEmpty();
    }

    @Test
    void reportsBrokenBlock() {
        assertThatThrownBy(() -> StudioCaptions.fromText("00:00:05,000 --> 00:00:01,000\nНазад"))
                .isInstanceOf(StudioException.class)
                .hasMessageContaining("блоке 1");
    }

    @Test
    void decodesCp1251Files() {
        byte[] data = "00:00:01,000 --> 00:00:02,000\nТекст".getBytes(Charset.forName("windows-1251"));
        assertThat(StudioCaptions.fromBytes(data)).extracting(StudioCaptions.Cue::text).containsExactly("Текст");
        byte[] utf = "﻿00:00:01,000 --> 00:00:02,000\nТекст".getBytes(StandardCharsets.UTF_8);
        assertThat(StudioCaptions.fromBytes(utf)).hasSize(1);
    }

    @Test
    void retimesSourceClockAroundTrimAndPause() {
        List<StudioCaptions.Cue> cues = List.of(
                new StudioCaptions.Cue(1, 3.0, "до обрезки"),
                new StudioCaptions.Cue(4, 8.0, "через паузу"));
        // фрагмент 2..12 с, пауза 5 с вставлена на 4-й секунде фрагмента (6 с исходника)
        List<StudioCaptions.Cue> result = StudioCaptions.retimeSource(cues, 2, 10, 4.0, 5);
        assertThat(result).containsExactly(
                new StudioCaptions.Cue(0, 1.0, "до обрезки"),
                new StudioCaptions.Cue(2, 4.0, "через паузу"),
                new StudioCaptions.Cue(9, 11.0, "через паузу"));
    }

    @Test
    void writesAssWithEscapedText() {
        StudioSettings settings = new StudioSettings();
        String ass = StudioCaptions.toAss(List.of(new StudioCaptions.Cue(0, 2.0, "a{b}\nc")), settings, 720, 1280, 10, null);
        assertThat(ass).contains("PlayResX: 720").contains("Dialogue: 0,0:00:00.00,0:00:02.00,Default,,0,0,0,,a\\{b\\}\\Nc");
        assertThatThrownBy(() -> StudioCaptions.toAss(List.of(new StudioCaptions.Cue(20, 30.0, "поздно")),
                settings, 720, 1280, 10, null)).isInstanceOf(StudioException.class);
    }
}
