package com.sentinelai.common.exception;

/** Thrown for invalid input or an illegal operation such as a disallowed state transition (HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
