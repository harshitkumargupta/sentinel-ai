package com.sentinelai.common.web;

import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.InvalidStateTransitionException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.exception.RateLimitException;
import com.sentinelai.common.web.ApiResponse.ApiError;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Malformed, unparseable, or oversized request bodies (incl. JSON depth/size-limit breaches). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                ApiError.of("MALFORMED_REQUEST", "Request body is missing, malformed, or too large"));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadParams(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ApiError.of("BAD_REQUEST", "Invalid or missing request parameter"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ApiError.of("METHOD_NOT_ALLOWED", "HTTP method not supported"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST,
                new ApiError("VALIDATION_ERROR", "Request validation failed", fields));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ApiError.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ApiError.of("NOT_FOUND", ex.getMessage()));
    }

    /** Unmapped path (e.g. a feature-flagged controller that is off): 404 in the standard body. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ApiError.of("NOT_FOUND", "No such endpoint"));
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Object>> handleConflict(Exception ex) {
        return build(HttpStatus.CONFLICT, ApiError.of("CONFLICT", "The request conflicts with existing data"));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiResponse<Object>> handleTransition(InvalidStateTransitionException ex) {
        return build(HttpStatus.CONFLICT, ApiError.of("INVALID_STATE_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimit(RateLimitException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ApiError.of("RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ApiError.of("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ApiError.of("FORBIDDEN", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        // Log the full detail server-side (with the MDC traceId already on the log line); never leak
        // stack traces, SQL, or internals to the client.
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ResponseEntity<ApiResponse<Object>> build(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).body(ApiResponse.error(error));
    }
}
