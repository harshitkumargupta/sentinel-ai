package com.sentinelai.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Uniform envelope for all API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, Object details) {
        public static ApiError of(String code, String message) {
            return new ApiError(code, message, null);
        }
    }
}
