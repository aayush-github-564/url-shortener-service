package com.aayush.url_shortener.service;

import com.aayush.url_shortener.model.Click;
import com.aayush.url_shortener.model.Url;
import com.aayush.url_shortener.repository.ClickRepository;
import com.aayush.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.aayush.url_shortener.dto.ClickAnalyticsResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    // How long we keep a URL cached in Redis (24 hours)
    private static final long CACHE_TTL_HOURS = 24;

    // Redis key prefix — avoids collisions if Redis is shared across services
    // e.g. "url:abc123" → "https://www.google.com"
    private static final String CACHE_PREFIX = "url:";

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;
    private final StringRedisTemplate redisTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SHORTEN A URL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a short URL entry in PostgreSQL.
     * If a custom alias is provided, use it; otherwise generate a random short code.
     * Returns the saved Url entity.
     */
    public Url shortenUrl(String longUrl, String customAlias, LocalDateTime expiresAt) {
        String shortCode = resolveShortCode(customAlias);

        Url url = Url.builder()
                .shortCode(shortCode)
                .longUrl(longUrl)
                .expiresAt(expiresAt)
                .build();

        Url saved = urlRepository.save(url);
        log.info("Shortened URL: {} → {}", shortCode, longUrl);
        return saved;
    }

    /**
     * Picks the short code to use.
     * If the user provided a custom alias, validate it's not taken.
     * Otherwise, generate a random unique one.
     */
    private String resolveShortCode(String customAlias) {
        if (customAlias != null && !customAlias.isBlank()) {
            if (urlRepository.existsByShortCode(customAlias)) {
                throw new IllegalArgumentException(
                        "Custom alias '" + customAlias + "' is already taken. Please choose a different one."
                );
            }
            return customAlias;
        }
        return generateUniqueShortCode();
    }

    /**
     * Generates a random 7-character alphanumeric code.
     * Keeps trying until it finds one that doesn't already exist in the DB.
     *
     * Why 7 characters? 62^7 = ~3.5 trillion combinations — effectively collision-proof
     * for any realistic load this service will see.
     */
    private String generateUniqueShortCode() {
        String code;
        do {
            code = ShortCodeGenerator.generate();
        } while (urlRepository.existsByShortCode(code));
        return code;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. RESOLVE A SHORT CODE → LONG URL  (with Redis caching)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a short code to its original long URL.
     *
     * Flow:
     *   1. Check Redis → if found (cache HIT), return immediately
     *   2. Check PostgreSQL → if not found, throw exception
     *   3. Check expiry → if expired, throw exception
     *   4. Store in Redis → so the next request is a cache HIT
     *   5. Return long URL
     */
    public String resolveLongUrl(String shortCode) {
        String cacheKey = CACHE_PREFIX + shortCode;

        // Step 1: Redis cache check
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            log.debug("Cache HIT for shortCode: {}", shortCode);
            return cachedUrl;
        }

        log.debug("Cache MISS for shortCode: {}", shortCode);

        // Step 2: PostgreSQL lookup
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No URL found for short code: " + shortCode
                ));

        // Step 3: Expiry check
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UrlExpiredException("Short URL '" + shortCode + "' has expired.");
        }

        // Step 4: Populate Redis cache for future requests
        redisTemplate.opsForValue().set(cacheKey, url.getLongUrl(), CACHE_TTL_HOURS, TimeUnit.HOURS);

        return url.getLongUrl();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. RECORD A CLICK
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves a Click record associated with the given Url entity.
     * Called by the Controller right after a successful redirect.
     *
     * The Controller extracts request metadata (IP, device, etc.) and passes it here.
     * We keep this logic in the service so it stays easy to test and extend.
     */
    public void recordClick(Url url, String ipAddress, String country,
                            String deviceType, String browser, String referer) {
        Click click = Click.builder()
                .url(url)
                .ipAddress(ipAddress)
                .country(country)
                .deviceType(deviceType)
                .browser(browser)
                .referer(referer)
                .build();

        clickRepository.save(click);
        log.debug("Recorded click for shortCode: {}", url.getShortCode());
    }

    /**
     * Convenience overload — resolves the short code AND records the click in one call.
     * Returns the long URL so the Controller can redirect immediately.
     *
     * Note: We intentionally look up the Url entity again here (not just the cached string)
     * so we have the full entity to associate with the Click record.
     */
    public String resolveAndRecord(String shortCode, String ipAddress, String country,
                                   String deviceType, String browser, String referer) {
        // Resolve via cache/DB — throws if not found or expired
        String longUrl = resolveLongUrl(shortCode);

        // Fetch entity for Click association (will almost always be cached in JPA's 1st-level cache)
        urlRepository.findByShortCode(shortCode).ifPresent(url ->
                recordClick(url, ipAddress, country, deviceType, browser, referer)
        );

        return longUrl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. ANALYTICS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds an analytics summary for a given short code.
     * Groups clicks by country, device, and browser using Java streams.
     */
    public ClickAnalyticsResponse getAnalytics(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No URL found for short code: " + shortCode
                ));

        List<Click> clicks = clickRepository.findByUrl(url);

        // Group clicks by each dimension using streams
        // e.g. {"IN": 30, "US": 12}
        Map<String, Long> byCountry = clicks.stream()
                .filter(c -> c.getCountry() != null)
                .collect(Collectors.groupingBy(Click::getCountry, Collectors.counting()));

        Map<String, Long> byDevice = clicks.stream()
                .filter(c -> c.getDeviceType() != null)
                .collect(Collectors.groupingBy(Click::getDeviceType, Collectors.counting()));

        Map<String, Long> byBrowser = clicks.stream()
                .filter(c -> c.getBrowser() != null)
                .collect(Collectors.groupingBy(Click::getBrowser, Collectors.counting()));

        return ClickAnalyticsResponse.builder()
                .shortCode(shortCode)
                .longUrl(url.getLongUrl())
                .totalClicks(clicks.size())
                .clicksByCountry(byCountry)
                .clicksByDevice(byDevice)
                .clicksByBrowser(byBrowser)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CUSTOM EXCEPTIONS (inner classes — keeps things self-contained for now)
    // ─────────────────────────────────────────────────────────────────────────

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class UrlExpiredException extends RuntimeException {
        public UrlExpiredException(String message) {
            super(message);
        }
    }
}