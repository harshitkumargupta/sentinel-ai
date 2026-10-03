package com.sentinelai.incident.dto;

import com.sentinelai.incident.domain.IncidentFeedback;
import jakarta.validation.constraints.NotNull;

public record UpdateFeedbackRequest(@NotNull IncidentFeedback feedback) {
}
