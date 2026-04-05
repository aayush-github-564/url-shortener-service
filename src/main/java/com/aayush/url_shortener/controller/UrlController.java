package com.aayush.url_shortener.controller;

import com.aayush.url_shortener.dto.ShortenRequest;
import com.aayush.url_shortener.dto.ShortenResponse;
import com.aayush.url_shortener.model.Url;
import com.aayush.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final UrlService urlService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/urls  →  Shorten a URL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Accepts a long URL and returns a short one.
     *
     * Request:  POST /api/urls
     *           Body: { "longUrl": "https://google.com", "customAlias": "google" }
     *
     * Response: 201 Created
     *           Body: { "shortCode": "google", "shortUrl": "http://localhost:8080/r/google", ... }
     *
     * @Valid triggers automatic validation of ShortenRequest fields.
     * If validation fails, Spring returns 400 Bad Request automatically.
     */
    @PostMapping("/api/urls")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest) {

        Url saved = urlService.shortenUrl(
                request.getLongUrl(),
                request.getCustomAlias(),
                request.getExpiresAt()
        );

        // Build the full clickable short URL using the incoming request's base URL
        String baseUrl = httpRequest.getRequestURL().toString()
                .replace("/api/urls", "");
        String shortUrl = baseUrl + "/r/" + saved.getShortCode();

        ShortenResponse response = ShortenResponse.builder()
                .shortCode(saved.getShortCode())
                .shortUrl(shortUrl)
                .longUrl(saved.getLongUrl())
                .expiresAt(saved.getExpiresAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /r/{shortCode}  →  Redirect to long URL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the short code and redirects the user to the original URL.
     * Also records the click with request metadata.
     *
     * Response: 302 Found  (browser follows the Location header automatically)
     *
     * This is the hot path — every redirect goes through here.
     * Redis cache is checked inside urlService.resolveAndRecord().
     */
    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest httpRequest) {

        // Extract metadata from the HTTP request for click tracking
        String ipAddress = getClientIp(httpRequest);
        String referer   = httpRequest.getHeader("Referer");
        String userAgent = httpRequest.getHeader("User-Agent");

        // Parse device type and browser from User-Agent string
        // (simple heuristic — good enough for analytics, interview-defensible)
        String deviceType = parseDeviceType(userAgent);
        String browser    = parseBrowser(userAgent);

        // Country would normally come from a GeoIP lookup (e.g. MaxMind).
        // For now we pass "UNKNOWN" — easy to swap in later.
        String country = "UNKNOWN";

        String longUrl = urlService.resolveAndRecord(
                shortCode, ipAddress, country, deviceType, browser, referer
        );

        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", longUrl);
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets the real client IP — checks X-Forwarded-For first (set by proxies/load balancers),
     * falls back to the direct connection IP.
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple User-Agent heuristic for device type detection.
     * Returns "mobile", "tablet", or "desktop".
     */
    private String parseDeviceType(String userAgent) {
        if (userAgent == null) return "unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile"))  return "mobile";
        if (ua.contains("tablet"))  return "tablet";
        return "desktop";
    }

    /**
     * Simple User-Agent heuristic for browser detection.
     * Order matters — Chrome UA also contains "Safari", so check Chrome first.
     */
    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg"))     return "Edge";
        if (ua.contains("chrome"))  return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari"))  return "Safari";
        return "Other";
    }
}