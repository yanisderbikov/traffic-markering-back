package ru.trafficmarkering.service.studio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;

import java.util.concurrent.TimeUnit;

/** Есть ли на машине FFmpeg: без него задачи копятся в очереди, сайт об этом предупреждает. */
@Slf4j
@Component
public class StudioRendererHealth {

    private final StudioProperties properties;
    private volatile Boolean available;

    public StudioRendererHealth(StudioProperties properties) {
        this.properties = properties;
    }

    public boolean available() {
        Boolean cached = available;
        if (cached == null) {
            cached = check(properties.getFfmpeg()) && check(properties.getFfprobe());
            available = cached;
            if (!cached) {
                log.warn("Видео-студия: ffmpeg/ffprobe не найдены, рендер недоступен");
            }
        }
        return cached;
    }

    private static boolean check(String binary) {
        try {
            Process process = new ProcessBuilder(binary, "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
