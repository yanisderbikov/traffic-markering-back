package ru.trafficmarkering.service.social;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.application.Platform;

import java.util.Locale;
import java.util.Map;

/** Площадки в путях /api/social/{platform}/…: те же слаги, что у OAuth-провайдеров. */
public final class SocialPlatforms {

    private static final Map<String, Platform> BY_SLUG = Map.of(
            "instagram", Platform.INSTAGRAM,
            "tiktok", Platform.TIKTOK,
            "youtube", Platform.YOUTUBE_SHORTS);

    private SocialPlatforms() {
    }

    public static Platform require(String slug) {
        Platform platform = slug == null ? null : BY_SLUG.get(slug.toLowerCase(Locale.ROOT));
        if (platform == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Площадка не поддерживается");
        }
        return platform;
    }
}
