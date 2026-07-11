package com.shop.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Validation errors — @Valid annotation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>>
    handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {
                    String field =
                            ((FieldError) error).getField();
                    String message =
                            error.getDefaultMessage();
                    errors.put(field, message);
                });

        String detailedMessage = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String field = error instanceof FieldError 
                            ? ((FieldError) error).getField() 
                            : error.getObjectName();
                    return field + ": " + error.getDefaultMessage();
                })
                .collect(java.util.stream.Collectors.joining("; "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message(detailedMessage)
                        .data(errors)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    // Not found errors — EntityNotFoundException → 404
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>>
    handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Object>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .data(null)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    // Business logic errors
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>>
    handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred: ", ex);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Object>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .data(null)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(BelowCostWarningException.class)
    public ResponseEntity<Map<String, Object>> handleBelowCostWarning(BelowCostWarningException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("warning", true);
        response.put("message", ex.getMessage());
        response.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // All other errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>>
    handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Object>builder()
                        .success(false)
                        .message("An internal server error occurred. Please contact the administrator.")
                        .data(null)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }
}