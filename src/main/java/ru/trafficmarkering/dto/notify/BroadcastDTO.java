package ru.trafficmarkering.dto.notify;

import com.fasterxml.jackson.annotation.JsonFormat;
import ru.trafficmarkering.model.notify.Broadcast;

import java.time.Instant;

public record BroadcastDTO(Long id, String audience, String channel, String title, String text, String buttonText,
                           String buttonUrl, String status, int total, int sent, int failed,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt) {

    public static BroadcastDTO from(Broadcast b) {
        return new BroadcastDTO(b.getId(), b.getAudience().name(), b.getChannel().name(), b.getTitle(), b.getText(),
                b.getButtonText(), b.getButtonUrl(), b.getStatus().name(), b.getTotal(), b.getSent(), b.getFailed(),
                b.getCreatedAt(), b.getFinishedAt());
    }
}
