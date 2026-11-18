package ru.trafficmarkering.model.application;

public record PayableViews(long views, boolean geographyKnown) {

    public static PayableViews of(long views) {
        return new PayableViews(Math.max(0L, views), true);
    }

    public static PayableViews withoutGeography() {
        return new PayableViews(0L, false);
    }
}
