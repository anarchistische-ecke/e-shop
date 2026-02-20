package com.example.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
