package com.example.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_ERROR", ex.getMessage(), true, List.of())
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError("STATE_CONFLICT", ex.getMessage(), true, List.of())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(
                        error.getField(),
                        error.getCode() != null ? error.getCode() : "INVALID_VALUE",
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
                ))
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_ERROR", "Validation failed", true, errors)
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new ApiError("DATA_STORE_UNAVAILABLE", message, false, List.of())
        );
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiError> handleUpstreamHttpError(RestClientResponseException ex) {
        log.error("Upstream HTTP error", ex);
        String responseBody = ex.getResponseBodyAsString();
        String message = ex.getStatusText();
        if (responseBody != null && !responseBody.isBlank()) {
            message = responseBody.length() > 500
                    ? responseBody.substring(0, 500) + "..."
                    : responseBody;
        } else if (message == null || message.isBlank()) {
            message = "Upstream service returned an HTTP error";
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new ApiError("UPSTREAM_SERVICE_ERROR", message, true, List.of())
        );
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleUpstreamError(RestClientException ex) {
        log.error("Upstream service error", ex);
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Upstream service is unavailable";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new ApiError("UPSTREAM_SERVICE_ERROR", message, true, List.of())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Internal server error", false, List.of()));
    }

    public record ApiError(String code, String message, boolean recoverable, List<FieldError> fieldErrors) {
    }

    public record FieldError(String field, String code, String message) {
    }
}
