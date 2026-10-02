package ru.trafficmarkering.service.social;

import java.time.Instant;

public record RefreshedToken(String accessToken, String refreshToken, Instant expiresAt) {
}
