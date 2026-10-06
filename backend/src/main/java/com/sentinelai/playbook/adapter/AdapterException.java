package com.sentinelai.playbook.adapter;

/** A transient enforcement-point failure (retryable by the executor). */
public class AdapterException extends RuntimeException {
    public AdapterException(String message) {
        super(message);
    }
}
