package ru.trafficmarkering.service.studio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Параметры монтажа. Хранятся в задаче и шаблоне как JSON.
 * Значения по умолчанию совпадают с исходным Offer Studio.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudioSettings {

    /** Встроенные фоны: id → название (картинки в resources/studio/presets) */
    public static final Map<String, String> PRESET_NAMES = presetNames();
    public static final List<String> PRESETS = List.copyOf(PRESET_NAMES.keySet());

    public static final Set<Integer> SIZE_LIMITS = Set.of(0, 10, 25, 49);
    public static final int MAX_SECONDS = 120;
    public static final int PAUSE_MAX_SECONDS = 30;

    // ── Баннер ──
    /** none | strip | overlay | pause */
    private String mode = "none";
    /** strip: top|bottom; overlay/pause: top|center|bottom */
    private String place = "bottom";
    private String align = "center";
    /** Начало показа (overlay) или точка вставки (pause), секунды от начала фрагмента */
    private Double start;
    private Double duration;
    /** Высота полосы 10–45% либо ширина баннера поверх видео 15–100% */
    private Double size;
    private double opacity = 100;
    private double overlayDim = 0;
    private double overlayDuck = 0;
    private boolean green = false;
    /** null — по умолчанию: звук баннера только в режиме паузы */
    private Boolean bannerAudio;
    private boolean bannerLoop = false;
    private String stripColor = "#1B1D1C";

    // ── Фон и компоновка ──
    /** off | preset | file | color | blur */
    private String background = "off";
    /** random или id встроенного фона */
    private String preset = "random";
    private String color = "#101111";
    /** fit | circle */
    private String shape = "fit";
    private double scale = 78;
    /** off | top | bottom — разделённый экран: второе видео идёт как background=file */
    private String splitPlace = "off";
    private double splitShare = 60;
    private String splitFit = "fill";

    // ── Экспорт ──
    /** source | vertical | square | horizontal | portrait */
    private String format = "source";
    /** 720 | 1080 */
    private String quality = "1080";
    private int sizeLimit = 0;
    /** single | vertical_square */
    private String exportProfile = "single";

    // ── Субтитры ──
    private String captionPosition = "bottom";
    private String captionScope = "main";
    /** output — таймкоды готового ролика, source — исходника */
    private String captionClock = "output";
    private String captionStyle = "outline";
    private String captionSize = "medium";

    // ── Исходник ──
    private double sourceStart = 0;
    private Double sourceEnd;
    private double sourceVolume = 100;
    /** Хранить исходники 24 часа для повтора */
    private boolean keepSource = false;

    private static Map<String, String> presetNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("ember", "Эмбер");
        names.put("carbon", "Карбон");
        names.put("electric", "Электрик");
        names.put("iris", "Ирис");
        names.put("pine", "Хвоя");
        names.put("latte", "Латте");
        names.put("ocean", "Океан");
        names.put("dusk", "Сумерки");
        names.put("sand", "Песок");
        names.put("mint", "Мята");
        names.put("steel", "Сталь");
        names.put("coral", "Коралл");
        names.put("ultraviolet", "Ультрафиолет");
        names.put("amber", "Янтарь");
        names.put("ice", "Лёд");
        names.put("deep-space", "Космос");
        return java.util.Collections.unmodifiableMap(names);
    }

    public boolean bannerAudioEffective() {
        return bannerAudio != null ? bannerAudio : "pause".equals(mode);
    }

    public double sizeEffective() {
        if (size != null) {
            return size;
        }
        return "strip".equals(mode) ? 20 : 90;
    }

    public boolean split() {
        return !"off".equals(splitPlace);
    }

    public StudioSettings copy() {
        StudioSettings c = new StudioSettings();
        c.mode = mode; c.place = place; c.align = align; c.start = start; c.duration = duration; c.size = size;
        c.opacity = opacity; c.overlayDim = overlayDim; c.overlayDuck = overlayDuck; c.green = green;
        c.bannerAudio = bannerAudio; c.bannerLoop = bannerLoop; c.stripColor = stripColor;
        c.background = background; c.preset = preset; c.color = color; c.shape = shape; c.scale = scale;
        c.splitPlace = splitPlace; c.splitShare = splitShare; c.splitFit = splitFit;
        c.format = format; c.quality = quality; c.sizeLimit = sizeLimit; c.exportProfile = exportProfile;
        c.captionPosition = captionPosition; c.captionScope = captionScope; c.captionClock = captionClock;
        c.captionStyle = captionStyle; c.captionSize = captionSize;
        c.sourceStart = sourceStart; c.sourceEnd = sourceEnd; c.sourceVolume = sourceVolume; c.keepSource = keepSource;
        return c;
    }

    /**
     * Проверка и нормализация. Бросает {@link StudioException} с понятным пользователю текстом.
     *
     * @param admin      администратор: без ограничений длительности
     * @param hasCaptions к задаче приложены субтитры (тогда «ничего не изменено» не ошибка)
     */
    public StudioSettings validate(boolean admin, boolean hasCaptions, boolean allowUnchanged) {
        oneOf("mode", mode, "none", "strip", "overlay", "pause");
        oneOf("background", background, "off", "preset", "file", "color", "blur");
        if ("strip".equals(mode)) {
            oneOf("place", place, "top", "bottom");
        } else {
            oneOf("place", place, "top", "center", "bottom");
        }
        oneOf("align", align, "left", "center", "right");
        double maxStart = admin ? Double.MAX_VALUE : MAX_SECONDS;
        double maxDuration = admin ? Double.MAX_VALUE : ("pause".equals(mode) ? PAUSE_MAX_SECONDS : MAX_SECONDS);
        if (start != null) {
            range("start", start, 0, maxStart);
        }
        if (duration != null) {
            range("duration", duration, 0.5, maxDuration);
        }
        if (size != null) {
            if ("strip".equals(mode)) {
                range("size", size, 10, 45);
            } else {
                range("size", size, 15, 100);
            }
        }
        range("opacity", opacity, 10, 100);
        range("overlayDim", overlayDim, 0, 100);
        range("overlayDuck", overlayDuck, 0, 100);
        hex("stripColor", stripColor);
        oneOf("splitPlace", splitPlace, "off", "top", "bottom");
        range("splitShare", splitShare, 30, 70);
        oneOf("splitFit", splitFit, "fill", "fit");
        if ("preset".equals(background)) {
            if (!"random".equals(preset) && !PRESETS.contains(preset)) {
                throw invalid();
            }
        }
        hex("color", color);
        oneOf("shape", shape, "fit", "circle");
        range("scale", scale, 35, 100);
        oneOf("format", format, "source", "vertical", "square", "horizontal", "portrait");
        oneOf("quality", quality, "720", "1080");
        if (!SIZE_LIMITS.contains(sizeLimit)) {
            throw invalid();
        }
        oneOf("exportProfile", exportProfile, "single", "vertical_square");
        oneOf("captionPosition", captionPosition, "top", "bottom");
        oneOf("captionScope", captionScope, "main", "frame");
        oneOf("captionClock", captionClock, "output", "source");
        oneOf("captionStyle", captionStyle, "outline", "yellow", "box");
        oneOf("captionSize", captionSize, "small", "medium", "large");
        range("sourceStart", sourceStart, 0, Double.MAX_VALUE);
        if (sourceEnd != null) {
            range("sourceEnd", sourceEnd, 0.5, Double.MAX_VALUE);
            if (sourceEnd - sourceStart < 0.5) {
                throw new StudioException("Оставьте хотя бы 0,5 секунды видео.");
            }
        }
        range("sourceVolume", sourceVolume, 0, 100);
        if (split() && !"file".equals(background)) {
            throw new StudioException("Для разделённого экрана добавьте второе видео.");
        }
        boolean sourceChanged = sourceStart > 0 || sourceEnd != null || sourceVolume != 100;
        if (!allowUnchanged && !sourceChanged && !hasCaptions && sizeLimit == 0
                && "single".equals(exportProfile) && "none".equals(mode)
                && "off".equals(background) && "source".equals(format) && "1080".equals(quality)) {
            throw new StudioException("Измените исходник, добавьте баннер, фон или субтитры либо настройте экспорт.");
        }
        return this;
    }

    private static void oneOf(String field, String value, String... allowed) {
        if (value == null) {
            throw invalid();
        }
        for (String option : allowed) {
            if (option.equals(value)) {
                return;
            }
        }
        throw invalid();
    }

    private static void range(String field, double value, double low, double high) {
        if (!Double.isFinite(value) || value < low || value > high) {
            throw invalid();
        }
    }

    private static void hex(String field, String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
            throw invalid();
        }
    }

    private static StudioException invalid() {
        return new StudioException("Проверьте параметры монтажа.");
    }
}
