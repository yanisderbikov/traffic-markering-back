package ru.trafficmarkering.service.social;

import java.time.Instant;

public record SocialAccountData(
        String externalId,
        String username,
        String displayName,
        String avatarUrl,
        Long followers,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String scopes
) {
}
