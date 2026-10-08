package com.sentinelai.playbook.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Propose a response action on an incident: an action type, an evidence target, an optional reason. */
public record ProposeActionRequest(
        @NotBlank @Size(max = 100) String actionType,
        @NotBlank @Size(max = 255) String target,
        @Size(max = 1000) String reason) {
}
