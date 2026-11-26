package ru.trafficmarkering.service.studio;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Субтитры: разбор SRT/VTT, пересчёт таймкодов и запись ASS для FFmpeg (libass). */
public final class StudioCaptions {

    public static final int LIMIT_BYTES = 256 * 1024;
    public static final int MAX_CUES = 2000;
    public static final Map<String, Double> SIZES = Map.of("small", .038, "medium", .05, "large", .065);

    /** Фраза субтитров; end == null — до конца ролика */
    public record Cue(double start, Double end, String text) {}

    private static final Pattern TIME = Pattern.compile("(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?:[.,](\\d{1,3}))?");
    private static final Pattern TIMING = Pattern.compile("\\s*(\\S+)\\s+-->\\s+(\\S+)(?:\\s+.*)?\\s*");
    private static final Pattern HEADER = Pattern.compile("^(?:WEBVTT|NOTE|STYLE|REGION)(?:[\\t ]|$).*");

    private StudioCaptions() {}

    /**
     * Текст из формы или файла: SRT/VTT разбирается по таймкодам,
     * обычный текст становится одной фразой на весь ролик.
     */
    public static List<Cue> fromText(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > LIMIT_BYTES) {
            throw new StudioException("Субтитры — до 256 КБ.");
        }
        if (source.contains("-->")) {
            return parseTimed(source);
        }
        return List.of(new Cue(0, null, clean(source.strip())));
    }

    public static List<Cue> fromBytes(byte[] data) {
        if (data == null || data.length == 0 || data.length > LIMIT_BYTES) {
            throw new StudioException("Выберите файл SRT или VTT до 256 КБ.");
        }
        return parseTimed(decode(data));
    }

    static String decode(byte[] data) {
        if (data.length >= 2 && ((data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) || (data[0] == (byte) 0xFE && data[1] == (byte) 0xFF))) {
            return new String(data, StandardCharsets.UTF_16);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("windows-1251"));
        }
    }

    static List<Cue> parseTimed(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n').strip();
        String[] blocks = normalized.split("\\n[\\t ]*\\n+");
        List<Cue> cues = new ArrayList<>();
        for (String block : blocks) {
            String[] lines = block.split("\n", -1);
            if (HEADER.matcher(lines[0]).matches()) {
                continue;
            }
            int timingIndex = lines[0].contains("-->") ? 0 : 1;
            Matcher timing = timingIndex < lines.length ? TIMING.matcher(lines[timingIndex]) : null;
            try {
                if (timing == null || !timing.matches()) {
                    throw new IllegalArgumentException();
                }
                double start = time(timing.group(1));
                double end = time(timing.group(2));
                StringBuilder payload = new StringBuilder();
                for (int i = timingIndex + 1; i < lines.length; i++) {
                    if (payload.length() > 0) {
                        payload.append('\n');
                    }
                    payload.append(lines[i]);
                }
                String text = payload.toString().replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>\\n]*>", "");
                text = unescape(text).strip();
                if (text.isEmpty() || !(0 <= start && start < end && end <= 86400)) {
                    throw new IllegalArgumentException();
                }
                cues.add(new Cue(start, end, clean(text)));
            } catch (IllegalArgumentException e) {
                throw new StudioException("Проверьте таймкоды и текст в блоке " + (cues.size() + 1) + ".");
            }
            if (cues.size() > MAX_CUES) {
                throw new StudioException("Один файл может содержать до 2000 фраз.");
            }
        }
        if (cues.isEmpty()) {
            throw new StudioException("В файле нет фраз с таймкодами.");
        }
        return cues;
    }

    static double time(String value) {
        Matcher m = TIME.matcher(value);
        if (!m.matches() || Integer.parseInt(m.group(2)) > 59 || Integer.parseInt(m.group(3)) > 59) {
            throw new IllegalArgumentException();
        }
        int hours = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        double fraction = Double.parseDouble("0." + (m.group(4) == null ? "0" : m.group(4)));
        return hours * 3600 + Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3)) + fraction;
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
    }

    /** Управляющие символы — в пробелы, переводы строк сохраняются */
    static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            out.append(ch == '\n' || (ch >= 32 && !(ch >= 127 && ch < 160)) ? ch : ' ');
        }
        return out.toString();
    }

    /** Таймкоды исходника → таймкоды готового ролика (обрезка и вставка паузы) */
    public static List<Cue> retimeSource(List<Cue> cues, double offset, double length, Double insertion, double delay) {
        double stop = offset + length;
        double[][] segments = insertion == null
                ? new double[][]{{offset, stop, 0}}
                : new double[][]{{offset, offset + insertion, 0}, {offset + insertion, stop, delay}};
        List<Cue> projected = new ArrayList<>();
        for (Cue cue : cues) {
            for (double[] seg : segments) {
                double first = Math.max(cue.start(), seg[0]);
                double last = Math.min(cue.end() != null ? cue.end() : stop, seg[1]);
                if (last > first) {
                    projected.add(new Cue(first - offset + seg[2], last - offset + seg[2], cue.text()));
                }
            }
        }
        return projected;
    }

    static String assTime(double seconds) {
        long total = Math.round(seconds * 100);
        long whole = total / 100;
        long fraction = total % 100;
        long minutes = whole / 60;
        long secs = whole % 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, secs, fraction);
    }

    /**
     * ASS-файл для фильтра ass.
     *
     * @param region null — весь кадр, иначе {y, height} области основного видео
     */
    public static String toAss(List<Cue> cues, StudioSettings settings, int width, int height, double duration, int[] region) {
        int size = Math.max(8, (int) Math.round(height * SIZES.getOrDefault(settings.getCaptionSize(), .05)));
        int alignment = "top".equals(settings.getCaptionPosition()) ? 8 : 2;
        int y = region == null ? 0 : region[0];
        int regionHeight = region == null ? height : region[1];
        int distance = alignment == 8 ? y : height - y - regionHeight;
        int margin = distance + Math.max(2, (int) Math.round(regionHeight * .08));
        int side = Math.max(2, (int) Math.round(width * .06));
        String style = settings.getCaptionStyle();
        String foreground = "yellow".equals(style) ? "&H0043DBFF" : "&H00FFFFFF";
        int borderStyle = "box".equals(style) ? 3 : 1;
        String outline = "box".equals(style) ? "&H28000000" : "&H00000000";
        StringBuilder out = new StringBuilder();
        out.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ").append(width)
                .append("\nPlayResY: ").append(height).append("\nWrapStyle: 0\n\n[V4+ Styles]\n")
                .append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, ")
                .append("Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, ")
                .append("Alignment, MarginL, MarginR, MarginV, Encoding\n")
                .append("Style: Default,DejaVu Sans,").append(size).append(',').append(foreground).append(',')
                .append(foreground).append(',').append(outline).append(",&H80000000,-1,0,0,0,100,100,0,0,")
                .append(borderStyle).append(',').append(Math.max(1, size / 13)).append(",1,").append(alignment)
                .append(',').append(side).append(',').append(side).append(',').append(margin).append(",1\n\n")
                .append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        int count = 0;
        for (Cue cue : cues) {
            double stop = Math.min(cue.end() != null ? cue.end() : duration, duration);
            if (stop <= cue.start() || assTime(cue.start()).equals(assTime(stop))) {
                continue;
            }
            String text = cue.text().replace("\\", "\\\u200b").replace("{", "\\{").replace("}", "\\}").replace("\n", "\\N");
            out.append("Dialogue: 0,").append(assTime(cue.start())).append(',').append(assTime(stop))
                    .append(",Default,,0,0,0,,").append(text).append('\n');
            count++;
        }
        if (count == 0) {
            throw new StudioException("Таймкоды субтитров должны попадать в длительность готового видео.");
        }
        return out.toString();
    }
}
