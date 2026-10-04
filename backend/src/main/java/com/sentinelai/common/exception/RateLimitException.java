package com.sentinelai.common.exception;

/** Thrown when a caller exceeds an allowed request rate (HTTP 429). */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
