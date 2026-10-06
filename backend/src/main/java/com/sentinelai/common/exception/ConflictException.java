package com.sentinelai.common.exception;

/** Thrown when a request conflicts with current state, e.g. a duplicate unique key (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
