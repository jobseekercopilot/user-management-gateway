package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.FieldViolation;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.web.PayloadTooLargeIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<GatewayResponse> validation(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::safeViolation)
                .distinct()
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return ResponseEntity.badRequest().body(GatewayResponse.validationFailure(violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<GatewayResponse> malformedJson(HttpMessageNotReadableException exception) {
        if (hasCause(exception, PayloadTooLargeIOException.class)) {
            return failure(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large.");
        }
        return failure(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<GatewayResponse> unsupportedMediaType() {
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<GatewayResponse> notFound() {
        return failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<GatewayResponse> unexpected() {
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private FieldViolation safeViolation(FieldError error) {
        String code = error.getCode() == null ? "INVALID" : error.getCode().toUpperCase(java.util.Locale.ROOT);
        return new FieldViolation(error.getField(), code);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<GatewayResponse> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(GatewayResponse.failure(status.value(), code, message));
    }
}
