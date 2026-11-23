package ru.trafficmarkering.dto.studio;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record StudioTelegramLinkDTO(String url, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt) {}
