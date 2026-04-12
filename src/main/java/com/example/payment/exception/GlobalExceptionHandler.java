package com.example.payment.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex,
                                                                    HttpServletRequest request) {
        if (isStripeLikePath(request.getRequestURI())) {
            FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type", "invalid_request_error");
            error.put("message", first != null ? first.getDefaultMessage() : "Validation failed");
            error.put("param", first != null ? first.getField() : null);
            error.put("code", "parameter_invalid");
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ValidationErrorResponse response = new ValidationErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
        response.setMessage("Validation failed");
        response.setPath(request.getRequestURI());
        response.setErrors(errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApi(ApiException ex, HttpServletRequest request) {
        if (isStripeLikePath(request.getRequestURI())) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type", mapErrorType(ex.getStatus()));
            error.put("message", ex.getMessage());
            error.put("code", ex.getStatus().is4xxClientError() ? "request_error" : "provider_error");
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", error));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    private boolean isStripeLikePath(String path) {
        return path.startsWith("/api/payment_intents") || path.startsWith("/api/refunds") || path.startsWith("/api/webhooks/stripe");
    }

    private String mapErrorType(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return "authentication_error";
        }
        if (status.is4xxClientError()) {
            return "invalid_request_error";
        }
        return "api_error";
    }
}
