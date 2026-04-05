package com.aayush.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response body for GET /api/urls/{shortCode}/analytics
 *
 * We return this JSON:
 * {
 *   "shortCode": "aB3xK7q",
 *   "longUrl": "https://www.google.com",
 *   "totalClicks": 42,
 *   "clicksByCountry": { "IN": 30, "US": 12 },
 *   "clicksByDevice":  { "mobile": 25, "desktop": 17 },
 *   "clicksByBrowser": { "Chrome": 35, "Safari": 7 }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickAnalyticsResponse {

    private String shortCode;
    private String longUrl;
    private long totalClicks;

    // Map of country → click count, e.g. {"IN": 30, "US": 12}
    private Map<String, Long> clicksByCountry;

    // Map of device type → click count, e.g. {"mobile": 25, "desktop": 17}
    private Map<String, Long> clicksByDevice;

    // Map of browser → click count, e.g. {"Chrome": 35, "Safari": 7}
    private Map<String, Long> clicksByBrowser;
}