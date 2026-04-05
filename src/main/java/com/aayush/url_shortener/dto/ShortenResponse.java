package com.aayush.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response body for POST /api/urls
 *
 * We return this JSON:
 * {
 *   "shortCode": "aB3xK7q",
 *   "shortUrl": "http://localhost:8080/r/aB3xK7q",
 *   "longUrl": "https://www.google.com",
 *   "expiresAt": "2025-12-31T00:00:00"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {

    private String shortCode;

    // The full clickable short URL the user can share
    private String shortUrl;

    private String longUrl;

    // null means the URL never expires
    private LocalDateTime expiresAt;
}