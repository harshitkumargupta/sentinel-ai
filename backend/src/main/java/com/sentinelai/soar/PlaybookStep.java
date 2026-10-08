package com.sentinelai.soar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One playbook step. {@code type}: CREATE_CASE (optional {@code priority}), NOTIFY (optional
 * {@code channelId}; default = every enabled channel), PROPOSE_BLOCK_IP, PROPOSE_DISABLE_USER,
 * ADD_TO_WATCHLIST ({@code set} = reference-set name).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaybookStep(StepType type, String priority, Long channelId, String set) {

    public enum StepType { CREATE_CASE, NOTIFY, PROPOSE_BLOCK_IP, PROPOSE_DISABLE_USER, ADD_TO_WATCHLIST }

    /** Outcome of one step in a run. */
    public record Result(StepType type, boolean ok, String detail) {
    }
}
