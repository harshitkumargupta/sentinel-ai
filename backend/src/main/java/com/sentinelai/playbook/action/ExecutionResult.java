package com.sentinelai.playbook.action;

/** Result of executing (or rolling back) an action, with before/after state for audit. */
public record ExecutionResult(boolean success, String beforeState, String afterState, String message) {
}
