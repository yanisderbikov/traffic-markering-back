package ru.trafficmarkering.model.studio;

public enum StudioJobStatus {
    QUEUED, RUNNING, DONE, FAILED, CANCELLED, EXPIRED;

    public boolean active() {
        return this == QUEUED || this == RUNNING;
    }
}
