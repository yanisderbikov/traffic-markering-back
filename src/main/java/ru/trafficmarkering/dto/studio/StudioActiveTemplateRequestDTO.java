package ru.trafficmarkering.dto.studio;

import java.util.UUID;

/** templateId == null — выключить автошаблон */
public record StudioActiveTemplateRequestDTO(UUID templateId, Boolean favorite) {}
