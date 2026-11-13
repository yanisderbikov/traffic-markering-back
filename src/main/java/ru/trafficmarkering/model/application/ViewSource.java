package ru.trafficmarkering.model.application;

public enum ViewSource {
    YOUTUBE_API("YouTube Data API"),
    TIKTOK_API("TikTok Display API"),
    INSTAGRAM_API("Instagram Graph API"),
    SCRAPE_CREATORS("ScrapeCreators"),
    MANUAL("Проставлено вручную");

    private final String description;

    ViewSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
