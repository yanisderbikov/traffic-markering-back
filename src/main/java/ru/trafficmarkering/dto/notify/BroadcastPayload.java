package ru.trafficmarkering.dto.notify;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Разложить рассылку по получателям */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastPayload {
    private Long broadcastId;
}
