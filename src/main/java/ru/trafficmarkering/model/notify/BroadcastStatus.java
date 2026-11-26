package ru.trafficmarkering.model.notify;

public enum BroadcastStatus {
    /** Ждёт, пока таска разложит её по получателям */
    QUEUED,
    SENDING,
    DONE
}
