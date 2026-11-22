package ru.trafficmarkering.service.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Сборка ролика через FFmpeg. Перенос media.py из Offer Studio один в один:
 * тот же граф фильтров, те же лимиты и тексты ошибок.
 */
@Slf4j
public class StudioRenderer {

    public static final int RATE = 30;
    public static final int MAX_DIMENSION = 4096;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Результат ffprobe; seconds == 0 — картинка */
    public record Media(Path path, String format, int video, Integer audio, int width, int height, double seconds) {
        Media withSeconds(double value) {
            return new Media(path, format, video, audio, width, height, value);
        }

        Media withoutAudio() {
            return new Media(path, format, video, null, width, height, seconds);
        }

        boolean image() {
            return format.endsWith("_pipe");
        }
    }

    /** Отмена задачи пользователем во время рендера */
    public static class CancelledException extends RuntimeException {
        public CancelledException() {
            super("cancelled");
        }
    }

    private final String ffmpeg;
    private final String ffprobe;
    private final long timeoutSeconds;

    public StudioRenderer(String ffmpeg, String ffprobe, long timeoutSeconds) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.timeoutSeconds = timeoutSeconds;
    }

    // ───────────────────────── inspect ─────────────────────────

    static String fileFormat(Path path) {
        byte[] h = new byte[16];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.readNBytes(h, 0, 16);
            if (read < 12) {
                throw unsupported();
            }
        } catch (IOException e) {
            throw unsupported();
        }
        String box = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
        if (List.of("ftyp", "moov", "mdat", "wide", "free").contains(box)) {
            return "mov";
        }
        if ((h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) {
            return "matroska";
        }
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png_pipe";
        }
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpeg_pipe";
        }
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp_pipe";
        }
        throw unsupported();
    }

    private static StudioException unsupported() {
        return new StudioException("Загрузите видео MP4, MOV, WebM или MKV либо картинку PNG, JPG или WebP.");
    }

    private static List<String> inputOptions(String format) {
        return List.of("-protocol_whitelist", "file", "-f", format);
    }

    public Media inspect(Path path, boolean videoRequired, boolean admin, boolean enforceLength) {
        String format = fileFormat(path);
        boolean image = format.endsWith("_pipe");
        if (videoRequired && image) {
            throw new StudioException("Первым файлом должно быть видео.");
        }
        JsonNode data;
        try {
            List<String> command = new ArrayList<>(List.of(ffprobe, "-v", "error"));
            command.addAll(inputOptions(format));
            command.addAll(List.of("-show_streams", "-show_format", "-of", "json", path.toString()));
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IOException("ffprobe failed");
            }
            data = JSON.readTree(out);
        } catch (IOException e) {
            throw unreadable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unreadable();
        }
        JsonNode video = null;
        Integer audio = null;
        for (JsonNode stream : data.path("streams")) {
            String type = stream.path("codec_type").asText();
            if ("video".equals(type) && video == null && stream.path("disposition").path("attached_pic").asInt(0) == 0) {
                video = stream;
            } else if ("audio".equals(type) && audio == null) {
                audio = stream.path("index").asInt();
            }
        }
        if (video == null) {
            throw unreadable();
        }
        int width = video.path("width").asInt(0);
        int height = video.path("height").asInt(0);
        double duration = 0;
        if (!image) {
            String raw = video.hasNonNull("duration") ? video.get("duration").asText()
                    : data.path("format").path("duration").asText("");
            try {
                duration = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw unreadable();
            }
        }
        if (!(16 <= Math.min(width, height) && Math.max(width, height) <= MAX_DIMENSION)) {
            throw new StudioException("Допустимый размер кадра — от 16 до 4096 пикселей по каждой стороне.");
        }
        if (!image && (!Double.isFinite(duration) || duration < 0.5)) {
            throw new StudioException("Длительность видео должна быть не меньше 0,5 секунды.");
        }
        if (enforceLength && !admin && duration > StudioSettings.MAX_SECONDS) {
            throw new StudioException("Длительность видео должна быть от 0,5 до 120 секунд.");
        }
        int rotation = 0;
        for (JsonNode side : video.path("side_data_list")) {
            if (side.has("rotation")) {
                rotation = side.get("rotation").asInt();
                break;
            }
        }
        if (Math.abs(rotation) % 180 == 90) {
            int t = width;
            width = height;
            height = t;
        }
        return new Media(path, format, video.path("index").asInt(), audio, width, height, duration);
    }

    private static StudioException unreadable() {
        return new StudioException("Этот файл не удалось прочитать. Попробуйте перекодировать его в MP4 или PNG.");
    }

    // ───────────────────────── geometry helpers ─────────────────────────

    static int even(double value) {
        return Math.max(2, ((int) value) / 2 * 2);
    }

    static String fit(int width, int height) {
        return "scale=" + width + ":" + height + ":force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1";
    }

    static String cover(int width, int height) {
        return "crop='max(2,trunc(min(iw,ih*" + width + "/" + height + ")/2)*2)':"
                + "'max(2,trunc(min(ih,iw*" + height + "/" + width + ")/2)*2)',scale=" + width + ":" + height + ",setsar=1";
    }

    static int[] outputSize(Media video, StudioSettings settings) {
        int edge = "720".equals(settings.getQuality()) ? 720 : 1080;
        int longEdge = edge * 16 / 9;
        switch (settings.getFormat()) {
            case "vertical":
                return new int[]{edge, longEdge};
            case "portrait":
                return new int[]{edge, edge * 5 / 4};
            case "square":
                return new int[]{edge, edge};
            case "horizontal":
                return new int[]{longEdge, edge};
            default:
                int maxW = video.width() > video.height() ? longEdge : edge;
                int maxH = video.width() > video.height() ? edge : longEdge;
                double factor = Math.min(1.0, Math.min((double) maxW / video.width(), (double) maxH / video.height()));
                return new int[]{even(video.width() * factor), even(video.height() * factor)};
        }
    }

    private static String f(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    static String videoFilter(Media media, int inputIndex) {
        return "[" + inputIndex + ":" + media.video() + "]setpts=PTS-STARTPTS,fps=" + RATE;
    }

    static String audioFilter(Media media, int inputIndex, double seconds) {
        String source = "anullsrc=r=48000:cl=stereo";
        if (media.audio() != null) {
            source = "[" + inputIndex + ":" + media.audio() + "]asetpts=PTS-STARTPTS,"
                    + "aresample=48000,aformat=sample_rates=48000:channel_layouts=stereo,apad";
        }
        return source + f(",atrim=duration=%.3f,asetpts=PTS-STARTPTS", seconds);
    }

    static String solid(String color, int width, int height, double seconds) {
        return f("color=c=0x%s:s=%dx%d:r=%d:d=%.3f", color, width, height, RATE, seconds);
    }

    private static void artwork(List<String> graph, Media video, Media background, int backgroundIndex,
                                int width, int contentHeight, StudioSettings settings) {
        String kind = settings.getBackground();
        double seconds = video.seconds();
        String source = videoFilter(video, 0) + ",";
        if ("blur".equals(kind)) {
            graph.add(source + "split=2[foreground][copy]");
            source = "[foreground]";
            graph.add("[copy]" + cover(width, contentHeight) + ",gblur=sigma=24:steps=2[base]");
        } else if (background != null) {
            graph.add(solid("101111", width, contentHeight, seconds) + "[matte]");
            graph.add(videoFilter(background, backgroundIndex) + "," + cover(width, contentHeight) + "[fill]");
            graph.add("[matte][fill]overlay=0:0:repeatlast=1[base]");
        } else {
            String color = "color".equals(kind) ? settings.getColor().substring(1) : "101111";
            graph.add(solid(color, width, contentHeight, seconds) + "[base]");
        }
        double scale = !"off".equals(kind) ? settings.getScale() / 100 : 1;
        if (!"off".equals(kind) && "circle".equals(settings.getShape())) {
            int diameter = even(Math.min(width, contentHeight) * scale);
            graph.add(source + cover(diameter, diameter) + ",format=yuv420p[crop]");
            graph.add("color=white:s=" + diameter + "x" + diameter + ":r=" + RATE + ":d=0.04,trim=end_frame=1,format=gray,"
                    + "geq=lum='clip((W/2-sqrt(pow(X-(W-1)/2,2)+pow(Y-(H-1)/2,2)))*255,0,255)'[mask]");
            graph.add("[crop][mask]alphamerge[fitted]");
        } else {
            graph.add(source + fit(even(width * scale), even(contentHeight * scale)) + "[fitted]");
        }
        graph.add("[base][fitted]overlay=(W-w)/2:(H-h)/2:repeatlast=1[main]");
    }

    /** {mainHeight, secondHeight, mainY} */
    static int[] splitGeometry(int height, StudioSettings settings) {
        if (height < 4) {
            throw new StudioException("Для двух видео выберите более высокий формат кадра.");
        }
        int mainHeight = Math.min(height - 2, even(height * settings.getSplitShare() / 100));
        int secondHeight = height - mainHeight;
        int mainY = "bottom".equals(settings.getSplitPlace()) ? secondHeight : 0;
        return new int[]{mainHeight, secondHeight, mainY};
    }

    private static void splitArtwork(List<String> graph, Media video, Media second, int secondIndex,
                                     int width, int height, StudioSettings settings) {
        int[] g = splitGeometry(height, settings);
        String framing = cover(width, g[0]);
        if ("fit".equals(settings.getSplitFit())) {
            framing = fit(width, g[0]) + ",pad=" + width + ":" + g[0] + ":(ow-iw)/2:(oh-ih)/2:color=0x101111";
        }
        double seconds = video.seconds();
        graph.add(videoFilter(video, 0) + f(",trim=duration=%.6f,", seconds) + framing
                + f(",tpad=stop_mode=clone:stop_duration=%.6f,trim=duration=%.6f,format=yuv420p[primary]", seconds, seconds));
        graph.add(videoFilter(second, secondIndex) + f(",trim=duration=%.6f,", seconds)
                + cover(width, g[1]) + ",format=yuv420p[auxiliary]");
        String parts = "bottom".equals(settings.getSplitPlace()) ? "[auxiliary][primary]" : "[primary][auxiliary]";
        graph.add(parts + "vstack=inputs=2:shortest=1[main]");
    }

    private static String bannerPicture(Media banner, int width, int height, StudioSettings settings) {
        String chain = videoFilter(banner, 1) + ",format=rgba";
        if (settings.isGreen()) {
            chain += ",colorkey=0x00FF00:0.25:0.12";
        }
        chain += "," + fit(width, height);
        if (settings.getOpacity() < 100) {
            chain += f(",format=rgba,colorchannelmixer=aa=%.2f", settings.getOpacity() / 100);
        }
        return chain;
    }

    private static void addPause(List<String> graph, Media banner, boolean withAudio, StudioSettings settings,
                                 int width, int height, double seconds, double start, double duration) {
        graph.add(solid("101111", width, height, duration) + "[backdrop]");
        graph.add(bannerPicture(banner, width, height, settings) + "[insert]");
        graph.add("[backdrop][insert]overlay=(W-w)/2:(H-h)/2:repeatlast=1,format=yuv420p,"
                + f("trim=duration=%.3f,setpts=PTS-STARTPTS[ad]", duration));
        String bannerSound = withAudio ? audioFilter(banner, 1, duration)
                : f("anullsrc=r=48000:cl=stereo,atrim=duration=%.3f", duration);
        graph.add(bannerSound + "[adaudio]");
        double point = Math.round(start * RATE) / (double) RATE;
        if (point <= 0) {
            graph.add("[ad][adaudio][main][mainaudio]concat=n=2:v=1:a=1[out][outaudio]");
        } else if (point >= seconds - 1.0 / RATE) {
            graph.add("[main][mainaudio][ad][adaudio]concat=n=2:v=1:a=1[out][outaudio]");
        } else {
            graph.add("[main]split[v1][v2]");
            graph.add(f("[v1]trim=end=%.5f,setpts=PTS-STARTPTS[first]", point));
            graph.add(f("[v2]trim=start=%.5f,setpts=PTS-STARTPTS[last]", point));
            graph.add("[mainaudio]asplit[a1][a2]");
            graph.add(f("[a1]atrim=end=%.5f,asetpts=PTS-STARTPTS[afirst]", point));
            graph.add(f("[a2]atrim=start=%.5f,asetpts=PTS-STARTPTS[alast]", point));
            graph.add("[first][afirst][ad][adaudio][last][alast]concat=n=3:v=1:a=1[out][outaudio]");
        }
    }

    static double sourceSegment(double length, double start, Double end, boolean admin) {
        if (end != null && end > length + 0.05) {
            throw new StudioException("Конец фрагмента выходит за пределы исходника.");
        }
        double selectedEnd = end == null ? length : Math.min(length, end);
        double result = selectedEnd - start;
        if (result < 0.5) {
            throw new StudioException("Оставьте хотя бы 0,5 секунды исходного видео.");
        }
        if (!admin && result > StudioSettings.MAX_SECONDS) {
            throw new StudioException("Длительность исходного фрагмента должна быть не больше 120 секунд.");
        }
        return result;
    }

    static int videoBudgetKbps(long sizeBytes, double seconds, int audioKbps) {
        int budget = (int) (sizeBytes * 8 * 0.90 / (1000 * seconds)) - audioKbps;
        if (budget < 80) {
            throw new StudioException("Ролик слишком длинный для выбранного размера. Обрежьте его или увеличьте лимит.");
        }
        return Math.min(budget, 24_000);
    }

    // ───────────────────────── render ─────────────────────────

    /**
     * Собирает folder/offer.mp4.
     *
     * @param folder     рабочая папка: video, banner?, background?
     * @param presetFile картинка встроенного фона (для background=preset), иначе null
     */
    public Path render(Path folder, StudioSettings settings, List<StudioCaptions.Cue> captions, Path presetFile,
                       boolean admin, IntConsumer progress, BooleanSupplier cancelled) {
        double sourceStart = settings.getSourceStart();
        Double sourceEnd = settings.getSourceEnd();
        boolean trimmed = sourceStart > 0 || sourceEnd != null;
        Media video = inspect(folder.resolve("video"), true, admin, !trimmed);
        double sourceVolume = settings.getSourceVolume();
        double length = sourceSegment(video.seconds(), sourceStart, sourceEnd, admin);
        video = video.withSeconds(length);
        if (sourceVolume == 0) {
            video = video.withoutAudio();
        }
        String mode = settings.getMode();
        String place = settings.getPlace();
        Media banner = !"none".equals(mode) ? inspect(folder.resolve("banner"), false, admin, true) : null;
        String backgroundKind = settings.getBackground();
        boolean split = settings.split();
        Media background = null;
        if ("file".equals(backgroundKind)) {
            background = inspect(folder.resolve("background"), false, true, false);
            if (split && background.seconds() == 0) {
                throw new StudioException("Для разделённого экрана загрузите второе видео.");
            }
        } else if (split) {
            throw new StudioException("Для разделённого экрана загрузите второе видео.");
        } else if ("preset".equals(backgroundKind)) {
            background = inspect(presetFile, false, admin, true);
        }
        int[] size = outputSize(video, settings);
        int width = size[0];
        int height = size[1];
        double seconds = video.seconds();
        Double durationSetting = settings.getDuration();
        Double startSetting = settings.getStart();
        double start = 0;
        double duration = 0;
        if ("overlay".equals(mode)) {
            start = startSetting == null ? 0 : startSetting;
            if (start >= seconds) {
                throw new StudioException("Баннер должен появиться до конца видео.");
            }
            duration = Math.min(durationSetting == null ? seconds : durationSetting, seconds - start);
        } else if ("pause".equals(mode)) {
            start = startSetting == null ? seconds / 2 : startSetting;
            if (start > seconds) {
                throw new StudioException("Момент вставки выходит за пределы видео.");
            }
            double sourceDuration = banner.seconds() > 0 ? banner.seconds() : 5;
            duration = durationSetting != null ? durationSetting
                    : (admin ? sourceDuration : Math.min(sourceDuration, StudioSettings.PAUSE_MAX_SECONDS));
        }

        double resultSeconds = seconds + ("pause".equals(mode) ? duration : 0);
        int band = "strip".equals(mode) ? even(height * settings.sizeEffective() / 100) : 0;
        int bodyHeight = height - band;
        String mainSound = audioFilter(video, 0, seconds);
        if (video.audio() != null && sourceVolume != 100) {
            mainSound += f(",volume=%.4f", sourceVolume / 100);
        }
        String active = f("gte(t,%.5f)*lt(t,%.5f)", start, start + duration);
        if ("overlay".equals(mode) && settings.getOverlayDuck() > 0) {
            mainSound += f(",volume=%.4f:enable='%s'", 1 - settings.getOverlayDuck() / 100, active);
        }
        List<String> graph = new ArrayList<>();
        graph.add(mainSound + "[mainaudio]");
        int backgroundIndex = banner != null ? 2 : 1;
        int[] captionRegion = null;
        if (split) {
            splitArtwork(graph, video, background, backgroundIndex, width, bodyHeight, settings);
            if ("main".equals(settings.getCaptionScope())) {
                int[] g = splitGeometry(bodyHeight, settings);
                captionRegion = new int[]{g[2] + ("top".equals(place) ? band : 0), g[0]};
            }
        } else {
            artwork(graph, video, background, backgroundIndex, width, bodyHeight, settings);
        }

        boolean withAudio = banner != null && banner.audio() != null && settings.bannerAudioEffective();
        String audio = "mainaudio";
        if ("strip".equals(mode)) {
            int mainY = "top".equals(place) ? band : 0;
            int bannerY = "top".equals(place) ? 0 : bodyHeight;
            String mat = settings.getStripColor().substring(1);
            graph.add("[main]pad=" + width + ":" + height + ":0:" + mainY + ":color=0x" + mat + "[strip]");
            graph.add(bannerPicture(banner, width, band, settings) + "[banner]");
            graph.add("[strip][banner]overlay=(W-w)/2:" + bannerY + "+(" + band + "-h)/2:repeatlast=1[out]");
        } else if ("overlay".equals(mode)) {
            String x = switch (settings.getAlign()) {
                case "left" -> "(W-w)*0.06";
                case "right" -> "(W-w)*0.94";
                default -> "(W-w)/2";
            };
            String y = switch (place) {
                case "top" -> "(H-h)*0.06";
                case "center" -> "(H-h)/2";
                default -> "(H-h)*0.94";
            };
            int bannerWidth = even(width * settings.sizeEffective() / 100);
            int bannerHeight = even(height * 0.7);
            String main = "[main]";
            if (settings.getOverlayDim() > 0) {
                graph.add(f("[main]drawbox=x=0:y=0:w=iw:h=ih:color=black@%.4f:t=fill:enable='%s'[dimmed]",
                        settings.getOverlayDim() / 100, active));
                main = "[dimmed]";
            }
            graph.add(bannerPicture(banner, bannerWidth, bannerHeight, settings)
                    + f(",tpad=stop_mode=clone:stop_duration=%.3f,setpts=PTS+%.5f/TB[banner]", duration, start));
            graph.add(main + "[banner]overlay=" + x + ":" + y + ":enable='" + active + "'[out]");
        } else if ("pause".equals(mode)) {
            addPause(graph, banner, withAudio, settings, width, height, seconds, start, duration);
            audio = "outaudio";
        } else {
            graph.add("[main]null[out]");
        }

        if (withAudio && ("strip".equals(mode) || "overlay".equals(mode))) {
            long delay = "overlay".equals(mode) ? Math.round(start * 1000) : 0;
            double soundDuration = "overlay".equals(mode) ? duration : seconds;
            graph.add(audioFilter(banner, 1, soundDuration) + ",volume=0.65,adelay=" + delay + ":all=1[banneraudio]");
            graph.add("[mainaudio][banneraudio]amix=inputs=2:duration=first:normalize=0,"
                    + "alimiter=limit=0.95:level=false:latency=true[outaudio]");
            audio = "outaudio";
        }

        List<String> inputs = new ArrayList<>(List.of("-threads", "2"));
        inputs.addAll(inputOptions(video.format()));
        if (sourceStart > 0) {
            inputs.addAll(List.of("-ss", f("%.6f", sourceStart)));
        }
        if (sourceEnd != null) {
            inputs.addAll(List.of("-t", f("%.6f", seconds)));
        }
        inputs.addAll(List.of("-i", video.path().toString()));
        Media[] extra = {banner, background};
        boolean[] loops = {settings.isBannerLoop(), true};
        for (int i = 0; i < extra.length; i++) {
            Media medium = extra[i];
            if (medium != null) {
                if (loops[i] && medium.seconds() > 0) {
                    inputs.addAll(List.of("-stream_loop", "-1"));
                }
                inputs.addAll(List.of("-threads", "2"));
                inputs.addAll(inputOptions(medium.format()));
                inputs.addAll(List.of("-i", medium.path().toString()));
            }
        }

        String videoOutput = "[out]";
        if (captions != null && !captions.isEmpty()) {
            List<StudioCaptions.Cue> cues = captions;
            if ("source".equals(settings.getCaptionClock())) {
                cues = StudioCaptions.retimeSource(cues, sourceStart, seconds,
                        "pause".equals(mode) ? start : null, "pause".equals(mode) ? duration : 0);
            }
            String ass = StudioCaptions.toAss(cues, settings, width, height, resultSeconds, captionRegion);
            try {
                Files.writeString(folder.resolve("captions.ass"), ass, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("captions write failed", e);
            }
            graph.add("[out]ass=captions.ass[captioned]");
            videoOutput = "[captioned]";
        }

        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y"));
        command.addAll(inputs);
        command.addAll(List.of("-filter_complex_threads", "2", "-filter_complex", String.join(";", graph),
                "-map", videoOutput, "-map", "[" + audio + "]", "-map_metadata", "-1", "-map_chapters", "-1",
                "-r", String.valueOf(RATE), "-c:v", "libx264", "-threads", "2", "-preset", "veryfast",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-movflags", "+faststart",
                "-t", f("%.3f", resultSeconds)));
        Path temporary = folder.resolve("render.mp4");
        Path target = folder.resolve("offer.mp4");
        try {
            encodeMp4(command, folder, temporary, resultSeconds, settings.getSizeLimit(), progress, cancelled,
                    admin ? null : timeoutSeconds);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("render move failed", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // временный файл удалится вместе с рабочей папкой
            }
        }
        return target;
    }

    private void encodeMp4(List<String> command, Path folder, Path target, double seconds, int sizeMb,
                           IntConsumer progress, BooleanSupplier cancelled, Long timeout) throws IOException {
        long limit = sizeMb * 1_000_000L;
        int audioKbps = sizeMb == 10 ? 64 : 96;
        Integer videoKbps = limit > 0 ? videoBudgetKbps(limit, seconds, audioKbps) : null;
        long deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        int attempts = limit > 0 ? 2 : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            List<String> full = new ArrayList<>(command);
            full.addAll(1, List.of("-progress", "pipe:1", "-nostats"));
            if (videoKbps == null) {
                full.addAll(List.of("-crf", "21", "-b:a", "128k"));
            } else {
                full.addAll(List.of("-b:v", videoKbps + "k", "-maxrate", videoKbps + "k",
                        "-bufsize", (videoKbps * 2) + "k", "-b:a", audioKbps + "k"));
            }
            full.add(target.toString());
            int base = limit > 0 ? (attempt == 0 ? 0 : 79) : 0;
            int span = limit > 0 ? (attempt == 0 ? 78 : 20) : 100;
            runWithProgress(full, folder, seconds, percent -> progress.accept(base + span * percent / 100),
                    cancelled, deadline);
            long actual = Files.size(target);
            if (actual == 0) {
                throw new StudioException("Получился пустой файл. Попробуйте другой исходник.");
            }
            if (limit == 0 || actual <= limit) {
                return;
            }
            videoKbps = (int) (videoKbps * (double) limit / actual * 0.83);
            if (videoKbps < 80) {
                break;
            }
        }
        throw new StudioException("Видео не поместилось в выбранный размер. Обрежьте его или выберите больший лимит.");
    }

    static Integer progressPercent(String line, double duration) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return null;
        }
        String key = line.substring(0, eq).strip();
        String value = line.substring(eq + 1).strip();
        try {
            double secs;
            if ("out_time_us".equals(key)) {
                secs = Long.parseLong(value) / 1_000_000.0;
            } else if ("out_time".equals(key)) {
                String[] parts = value.split(":");
                secs = Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Double.parseDouble(parts[2]);
            } else {
                return null;
            }
            return Math.max(0, Math.min(99, (int) (secs / duration * 100)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void runWithProgress(List<String> command, Path folder, double duration, IntConsumer progress,
                                 BooleanSupplier cancelled, long deadline) throws IOException {
        Process process = new ProcessBuilder(command).directory(folder.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        int last = 0;
        long lastCancelCheck = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (System.nanoTime() > deadline) {
                    process.destroyForcibly();
                    throw new StudioException("Обработка заняла слишком много времени. Попробуйте видео покороче.");
                }
                Integer percent = progressPercent(line, duration);
                if (percent != null && percent > last) {
                    last = percent;
                    progress.accept(percent);
                }
                if (System.nanoTime() - lastCancelCheck > TimeUnit.SECONDS.toNanos(2)) {
                    lastCancelCheck = System.nanoTime();
                    if (cancelled.getAsBoolean()) {
                        process.destroyForcibly();
                        throw new CancelledException();
                    }
                }
            }
            long remaining = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()));
            if (!process.waitFor(Math.min(remaining, 3600), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new StudioException("Обработка заняла слишком много времени. Попробуйте видео покороче.");
            }
            if (process.exitValue() != 0) {
                throw new StudioException("Не удалось собрать видео. Попробуйте другой исходник или баннер.");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CancelledException();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
