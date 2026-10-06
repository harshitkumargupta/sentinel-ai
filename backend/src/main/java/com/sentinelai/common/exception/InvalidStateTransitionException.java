package com.sentinelai.common.exception;

/** Thrown when an incident status change is not a legal transition (HTTP 409). */
public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
