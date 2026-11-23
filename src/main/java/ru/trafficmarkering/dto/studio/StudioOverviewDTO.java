package ru.trafficmarkering.dto.studio;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudioOverviewDTO(
        boolean admin,
        boolean unlimited,
        int dailyLimit,
        int remaining,
        int maxActive,
        int active,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant quotaResetsAt,
        long sourceMaxBytes,
        long bannerMaxBytes,
        long backgroundMaxBytes,
        boolean rendererAvailable,
        UUID activeTemplateId,
        UUID favoriteTemplateId,
        Telegram telegram,
        List<Preset> presets
) {
    public record Telegram(boolean botEnabled, String botUsername, String botUrl, boolean linked,
                           String telegramUsername, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant linkedAt) {}

    public record Preset(String id, String name, String url) {}
}
