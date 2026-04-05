package com.aayush.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request body for POST /api/urls
 *
 * The user sends this JSON:
 * {
 *   "longUrl": "https://www.google.com",
 *   "customAlias": "google",        ← optional
 *   "expiresAt": "2025-12-31T00:00:00"  ← optional
 * }
 */
@Data
public class ShortenRequest {

    @NotBlank(message = "longUrl must not be blank")
    private String longUrl;

    // Optional: only alphanumeric + hyphens, 3–30 chars
    @Pattern(
        regexp = "^[a-zA-Z0-9-]*$",
        message = "Custom alias can only contain letters, numbers, and hyphens"
    )
    @Size(min = 3, max = 30, message = "Custom alias must be between 3 and 30 characters")
    private String customAlias;

    // Optional: if null, URL never expires
    private LocalDateTime expiresAt;
}