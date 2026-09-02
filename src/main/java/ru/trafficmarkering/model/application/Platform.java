package ru.trafficmarkering.model.application;

/**
 * Площадка, где криатор выложил ролик. От неё зависит, какой счётчик просмотров
 * когда-нибудь сможет опросить ViewCountProvider.
 */
public enum Platform {
    TELEGRAM("Telegram"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    YOUTUBE_SHORTS("YouTube Shorts");

    private final String description;

    Platform(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
