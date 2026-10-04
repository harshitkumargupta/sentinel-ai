package com.sentinelai.ai.llm;

/**
 * The LLM could not be used for this call (timeout, transport error, open circuit, or exhausted
 * budget/quota). Signals the caller to fall back to deterministic output — it is never surfaced to
 * the client as an error.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
