package ru.trafficmarkering.dto.studio;

import java.time.Instant;

public record StudioTelegramLinkDTO(String url, Instant expiresAt) {}
