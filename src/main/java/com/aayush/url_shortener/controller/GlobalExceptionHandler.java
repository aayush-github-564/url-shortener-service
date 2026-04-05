package com.aayush.url_shortener.controller;

import com.aayush.url_shortener.service.UrlService.ResourceNotFoundException;
import com.aayush.url_shortener.service.UrlService.UrlExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Catches exceptions thrown anywhere in the application and converts them
 * into clean JSON HTTP responses.
 *
 * @RestControllerAdvice means: "watch all controllers and intercept exceptions"
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — short code not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "error", ex.getMessage()
        ));
    }

    // 410 Gone — URL existed but has expired
    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleExpired(UrlExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "status", 410,
                "error", ex.getMessage()
        ));
    }

    // 400 — custom alias already taken
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", 400,
                "error", ex.getMessage()
        ));
    }

    // 400 — @Valid validation failed (e.g. blank longUrl, bad alias format)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", 400,
                "error", message
        ));
    }
}