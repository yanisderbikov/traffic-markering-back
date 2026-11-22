package ru.trafficmarkering.service.studio;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Геометрия — всегда; настоящий рендер — если на машине есть FFmpeg. */
class StudioRendererTest {

    private static boolean ffmpeg;

    @BeforeAll
    static void detect() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            ffmpeg = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            ffmpeg = false;
        }
    }

    private static StudioRenderer.Media media(int w, int h) {
        return new StudioRenderer.Media(Path.of("x"), "mov", 0, 1, w, h, 10);
    }

    @Test
    void outputSizeFollowsFormatAndQuality() {
        StudioSettings s = new StudioSettings();
        s.setFormat("vertical");
        assertThat(StudioRenderer.outputSize(media(1920, 1080), s)).containsExactly(1080, 1920);
        s.setQuality("720");
        assertThat(StudioRenderer.outputSize(media(1920, 1080), s)).containsExactly(720, 1280);
        s.setFormat("source");
        assertThat(StudioRenderer.outputSize(media(3840, 2160), s)).containsExactly(1280, 720);
        assertThat(StudioRenderer.outputSize(media(500, 300), s)).containsExactly(500, 300);
    }

    @Test
    void splitGeometryPutsMainPartOnRequestedSide() {
        StudioSettings s = new StudioSettings();
        s.setSplitPlace("bottom");
        s.setSplitShare(60);
        assertThat(StudioRenderer.splitGeometry(1920, s)).containsExactly(1152, 768, 768);
        s.setSplitPlace("top");
        assertThat(StudioRenderer.splitGeometry(1920, s)[2]).isZero();
    }

    @Test
    void sourceSegmentChecksBounds() {
        assertThat(StudioRenderer.sourceSegment(30, 5, 15.0, false)).isEqualTo(10);
        assertThatThrownBy(() -> StudioRenderer.sourceSegment(30, 5, 40.0, false)).hasMessageContaining("за пределы");
        assertThatThrownBy(() -> StudioRenderer.sourceSegment(300, 0, null, false)).hasMessageContaining("120");
        assertThat(StudioRenderer.sourceSegment(300, 0, null, true)).isEqualTo(300);
    }

    @Test
    void progressParsing() {
        assertThat(StudioRenderer.progressPercent("out_time_us=5000000", 10)).isEqualTo(50);
        assertThat(StudioRenderer.progressPercent("out_time=00:00:09.990000", 10)).isEqualTo(99);
        assertThat(StudioRenderer.progressPercent("frame=12", 10)).isNull();
    }

    @Test
    void sizeBudget() {
        assertThat(StudioRenderer.videoBudgetKbps(25_000_000, 60, 96)).isEqualTo(2904);
        assertThatThrownBy(() -> StudioRenderer.videoBudgetKbps(10_000_000, 1200, 64)).isInstanceOf(StudioException.class);
    }

    @Test
    void rejectsUnknownFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("video");
        Files.writeString(file, "definitely not a video file");
        assertThatThrownBy(() -> StudioRenderer.fileFormat(file)).hasMessageContaining("MP4");
    }

    private static void generate(Path target, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-v", "error", "-y"));
        command.addAll(List.of(args));
        command.add(target.toString());
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isZero();
    }

    @Test
    void rendersVerticalWithBlurBannerOverlayAndTrim(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(ffmpeg, "ffmpeg не установлен");
        generate(dir.resolve("video"), "-f", "lavfi", "-i", "testsrc=size=640x360:rate=30:duration=4",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=4", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-shortest", "-f", "mp4");
        generate(dir.resolve("banner"), "-f", "lavfi", "-i", "color=c=green:size=300x100:duration=1",
                "-frames:v", "1", "-update", "1", "-f", "image2", "-c:v", "png");
        StudioSettings s = new StudioSettings();
        s.setFormat("vertical");
        s.setQuality("720");
        s.setBackground("blur");
        s.setMode("overlay");
        s.setStart(0.5);
        s.setDuration(1.0);
        s.setOverlayDim(30);
        s.setSourceStart(1);
        s.setSourceEnd(3.5);
        s.setSourceVolume(50);
        StudioRenderer renderer = new StudioRenderer("ffmpeg", "ffprobe", 120);
        List<Integer> progress = new ArrayList<>();
        Path out = renderer.render(dir, s, List.of(), null, false, progress::add, () -> false);
        StudioRenderer.Media result = renderer.inspect(out, true, false, true);
        assertThat(result.width()).isEqualTo(720);
        assertThat(result.height()).isEqualTo(1280);
        assertThat(result.seconds()).isBetween(2.3, 2.7);
        assertThat(result.audio()).isNotNull();
        assertThat(progress).isNotEmpty();
    }

    @Test
    void rendersCirclePresetWithPauseInsert(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(ffmpeg, "ffmpeg не установлен");
        generate(dir.resolve("video"), "-f", "lavfi", "-i", "testsrc=size=480x480:rate=30:duration=3",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-f", "mp4");
        generate(dir.resolve("banner"), "-f", "lavfi", "-i", "color=c=red:size=320x240:duration=1",
                "-frames:v", "1", "-update", "1", "-f", "image2", "-c:v", "png");
        Path preset = dir.resolve("preset.jpg");
        try (InputStream in = getClass().getResourceAsStream("/studio/presets/ocean.jpg")) {
            Files.copy(in, preset);
        }
        StudioSettings s = new StudioSettings();
        s.setFormat("square");
        s.setQuality("720");
        s.setBackground("preset");
        s.setPreset("ocean");
        s.setShape("circle");
        s.setMode("pause");
        s.setStart(1.0);
        s.setDuration(1.0);
        StudioRenderer renderer = new StudioRenderer("ffmpeg", "ffprobe", 120);
        Path out = renderer.render(dir, s, List.of(), preset, false, p -> {}, () -> false);
        StudioRenderer.Media result = renderer.inspect(out, true, false, true);
        assertThat(result.width()).isEqualTo(720);
        assertThat(result.seconds()).isBetween(3.8, 4.2);
    }

    @Test
    void cancellationStopsFfmpeg(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(ffmpeg, "ffmpeg не установлен");
        generate(dir.resolve("video"), "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30:duration=20",
                "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-f", "mp4");
        StudioSettings s = new StudioSettings();
        s.setBackground("blur");
        s.setFormat("vertical");
        StudioRenderer renderer = new StudioRenderer("ffmpeg", "ffprobe", 120);
        assertThatThrownBy(() -> renderer.render(dir, s, List.of(), null, false, p -> {}, () -> true))
                .isInstanceOf(StudioRenderer.CancelledException.class);
    }
}
