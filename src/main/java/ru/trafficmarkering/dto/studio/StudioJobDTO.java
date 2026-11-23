package ru.trafficmarkering.dto.studio;

import ru.trafficmarkering.service.studio.StudioSettings;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record StudioJobDTO(
        UUID id,
        String status,
        String origin,
        String filename,
        int progress,
        /** Сколько задач ждёт раньше этой (для QUEUED) */
        Long queuePosition,
        String error,
        StudioSettings settings,
        String preset,
        String presetName,
        UUID batchId,
        Long resultSize,
        String downloadUrl,
        String previewUrl,
        /** До какого момента доступен повтор; null — исходники не сохранены */
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant retryUntil,
        /** До какого момента хранится готовый файл */
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant availableUntil,
        boolean canSendToTelegram,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt
) {}
