package com.aayush.url_shortener.controller;

import com.aayush.url_shortener.dto.ClickAnalyticsResponse;
import com.aayush.url_shortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class AnalyticsController {

    private final UrlService urlService;

    /**
     * Returns click analytics for a given short code.
     *
     * Request:  GET /api/urls/{shortCode}/analytics
     * Response: 200 OK
     *           Body: { "totalClicks": 42, "clicksByCountry": {...}, ... }
     */
    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<ClickAnalyticsResponse> getAnalytics(
            @PathVariable String shortCode) {

        ClickAnalyticsResponse analytics = urlService.getAnalytics(shortCode);
        return ResponseEntity.ok(analytics);
    }
}