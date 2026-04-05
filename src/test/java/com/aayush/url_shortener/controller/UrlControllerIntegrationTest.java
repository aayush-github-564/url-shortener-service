package com.aayush.url_shortener.controller;

import com.aayush.url_shortener.service.UrlService;
import com.aayush.url_shortener.service.UrlService.ResourceNotFoundException;
import com.aayush.url_shortener.service.UrlService.UrlExpiredException;
import com.aayush.url_shortener.model.Url;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UrlController.
 *
 * @WebMvcTest spins up only the web layer (controllers, filters, exception handlers)
 * without starting a full Spring context or connecting to any database.
 * This makes it fast while still testing real HTTP behaviour.
 *
 * MockMvc lets us fire HTTP requests at our controllers and assert on
 * status codes, headers, and response body — all without a real server.
 */
@WebMvcTest(controllers = {UrlController.class, AnalyticsController.class, GlobalExceptionHandler.class})
class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/urls
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void postShortenUrl_withValidRequest_shouldReturn201WithShortCode() throws Exception {
        Url savedUrl = Url.builder()
                .shortCode("abc1234")
                .longUrl("https://www.google.com")
                .build();

        when(urlService.shortenUrl(anyString(), any(), any())).thenReturn(savedUrl);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\": \"https://www.google.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.longUrl").value("https://www.google.com"))
                .andExpect(jsonPath("$.shortUrl").exists());
    }

    @Test
    void postShortenUrl_withBlankLongUrl_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postShortenUrl_withDuplicateAlias_shouldReturn400() throws Exception {
        when(urlService.shortenUrl(anyString(), eq("taken"), any()))
                .thenThrow(new IllegalArgumentException("Custom alias 'taken' is already taken."));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\": \"https://www.google.com\", \"customAlias\": \"taken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /r/{shortCode}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void redirect_withValidShortCode_shouldReturn302WithLocationHeader() throws Exception {
        when(urlService.resolveAndRecord(eq("abc1234"), any(), any(), any(), any(), any()))
                .thenReturn("https://www.google.com");

        mockMvc.perform(get("/r/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.google.com"));
    }

    @Test
    void redirect_withUnknownShortCode_shouldReturn404() throws Exception {
        when(urlService.resolveAndRecord(eq("missing"), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("No URL found for short code: missing"));

        mockMvc.perform(get("/r/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void redirect_withExpiredShortCode_shouldReturn410() throws Exception {
        when(urlService.resolveAndRecord(eq("expired"), any(), any(), any(), any(), any()))
                .thenThrow(new UrlExpiredException("Short URL 'expired' has expired."));

        mockMvc.perform(get("/r/expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }
}