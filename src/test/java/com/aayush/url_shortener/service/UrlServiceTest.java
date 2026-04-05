package com.aayush.url_shortener.service;

import com.aayush.url_shortener.model.Url;
import com.aayush.url_shortener.repository.ClickRepository;
import com.aayush.url_shortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UrlService.
 *
 * We use Mockito to mock all external dependencies (repositories, Redis)
 * so we can test the service logic in complete isolation — no database,
 * no Redis, no Spring context needed. These tests run in milliseconds.
 *
 * @Mock      — creates a fake version of the class
 * @InjectMocks — creates a real UrlService and injects the mocks into it
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ClickRepository clickRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        // StringRedisTemplate.opsForValue() must return our mocked ValueOperations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // shortenUrl tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shortenUrl_withCustomAlias_shouldSaveWithThatAlias() {
        // Arrange
        when(urlRepository.existsByShortCode("myalias")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Url result = urlService.shortenUrl("https://www.google.com", "myalias", null);

        // Assert
        assertEquals("myalias", result.getShortCode());
        assertEquals("https://www.google.com", result.getLongUrl());
        verify(urlRepository).save(any(Url.class));
    }

    @Test
    void shortenUrl_withDuplicateCustomAlias_shouldThrowIllegalArgumentException() {
        // Arrange — alias already exists in DB
        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> urlService.shortenUrl("https://www.google.com", "taken", null));

        verify(urlRepository, never()).save(any());
    }

    @Test
    void shortenUrl_withNoAlias_shouldGenerateAShortCode() {
        // Arrange
        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Url result = urlService.shortenUrl("https://www.github.com", null, null);

        // Assert — code was auto-generated, should be 7 chars
        assertNotNull(result.getShortCode());
        assertEquals(7, result.getShortCode().length());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolveLongUrl tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resolveLongUrl_whenCacheHit_shouldReturnCachedUrlWithoutHittingDatabase() {
        // Arrange — Redis has the value
        when(valueOperations.get("url:abc1234")).thenReturn("https://www.google.com");

        // Act
        String result = urlService.resolveLongUrl("abc1234");

        // Assert
        assertEquals("https://www.google.com", result);

        // CRITICAL: database should never be touched on a cache hit
        verify(urlRepository, never()).findByShortCode(any());
    }

    @Test
    void resolveLongUrl_whenCacheMiss_shouldQueryDatabaseAndPopulateCache() {
        // Arrange — Redis has nothing, DB has the record
        when(valueOperations.get("url:abc1234")).thenReturn(null);

        Url url = Url.builder()
                .shortCode("abc1234")
                .longUrl("https://www.google.com")
                .build();
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        // Act
        String result = urlService.resolveLongUrl("abc1234");

        // Assert
        assertEquals("https://www.google.com", result);

        // Cache should now be populated
        verify(valueOperations).set(eq("url:abc1234"), eq("https://www.google.com"), anyLong(), any());
    }

    @Test
    void resolveLongUrl_whenShortCodeNotFound_shouldThrowResourceNotFoundException() {
        // Arrange — nothing in Redis or DB
        when(valueOperations.get(anyString())).thenReturn(null);
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UrlService.ResourceNotFoundException.class,
                () -> urlService.resolveLongUrl("missing"));
    }

    @Test
    void resolveLongUrl_whenUrlExpired_shouldThrowUrlExpiredException() {
        // Arrange — URL exists but expired in the past
        when(valueOperations.get(anyString())).thenReturn(null);

        Url expiredUrl = Url.builder()
                .shortCode("expired")
                .longUrl("https://www.google.com")
                .expiresAt(LocalDateTime.now().minusDays(1)) // expired yesterday
                .build();
        when(urlRepository.findByShortCode("expired")).thenReturn(Optional.of(expiredUrl));

        // Act & Assert
        assertThrows(UrlService.UrlExpiredException.class,
                () -> urlService.resolveLongUrl("expired"));
    }

    @Test
    void resolveLongUrl_whenUrlHasFutureExpiry_shouldResolveSuccessfully() {
        // Arrange — URL exists and expires tomorrow
        when(valueOperations.get(anyString())).thenReturn(null);

        Url validUrl = Url.builder()
                .shortCode("valid")
                .longUrl("https://www.google.com")
                .expiresAt(LocalDateTime.now().plusDays(1)) // expires tomorrow
                .build();
        when(urlRepository.findByShortCode("valid")).thenReturn(Optional.of(validUrl));

        // Act
        String result = urlService.resolveLongUrl("valid");

        // Assert
        assertEquals("https://www.google.com", result);
    }
}