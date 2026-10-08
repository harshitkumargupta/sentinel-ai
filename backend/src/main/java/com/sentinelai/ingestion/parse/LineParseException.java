package com.sentinelai.ingestion.parse;

/** A line that doesn't match its declared format. Counted as a parse error, never fatal to a batch. */
public class LineParseException extends RuntimeException {

    public LineParseException(String message) {
        super(message);
    }
}
