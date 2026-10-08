package com.sentinelai.search.query;

import com.sentinelai.common.exception.BadRequestException;

/** A search query that doesn't parse; the message names the position and what was expected. */
public class QuerySyntaxException extends BadRequestException {

    public QuerySyntaxException(String message) {
        super(message);
    }
}
