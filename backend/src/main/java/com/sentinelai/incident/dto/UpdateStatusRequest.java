package com.sentinelai.incident.dto;

import com.sentinelai.incident.domain.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull IncidentStatus status) {
}
