package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlMetadataResponse;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.exception.Rfc7807GlobalExceptionHandler;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.observability.UrlMetricsService;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for UrlController using @WebMvcTest.
 * Tests REST API endpoints in isolation with mocked service layer.
 */
@WebMvcTest(controllers = UrlController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    },
    properties = {
        "urlshortener.rate-limit.enabled=false"
    })
@org.springframework.test.context.ActiveProfiles("test")
@Import(Rfc7807GlobalExceptionHandler.class)
@DisplayName("UrlController Slice Tests")
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @MockBean
    private UrlMetricsService metricsService;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_CODE = "abc123d";
    private static final String TEST_LONG_URL = "https://www.example.com/very/long/path/to/resource";

    // ==================== POST /api/urls Tests ====================

    @Test
    @DisplayName("Should return 201 Created when valid URL submitted")
    void shouldReturn201_WhenValidUrlSubmitted() throws Exception {
        // Given
        Instant now = Instant.now();
        Instant expiresAt = now.plus(365, ChronoUnit.DAYS);

        UrlResponse mockResponse = new UrlResponse(
            TEST_CODE,
            BASE_URL + "/r/" + TEST_CODE,
            TEST_LONG_URL,
            now,
            expiresAt
        );

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
            .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": "https://www.example.com/very/long/path/to/resource"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", BASE_URL + "/r/" + TEST_CODE))
            .andExpect(jsonPath("$.code").value(TEST_CODE))
            .andExpect(jsonPath("$.shortUrl").value(BASE_URL + "/r/" + TEST_CODE))
            .andExpect(jsonPath("$.longUrl").value(TEST_LONG_URL))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.expiresAt").exists());

        verify(urlService).createShortUrl(any(CreateUrlRequest.class));
        verify(metricsService).recordUrlCreated();
    }

    @Test
    @DisplayName("Should return 201 Created when valid URL with custom expiration submitted")
    void shouldReturn201_WhenValidUrlWithExpirationSubmitted() throws Exception {
        // Given
        Instant now = Instant.now();
        Instant customExpiration = now.plus(30, ChronoUnit.DAYS);

        UrlResponse mockResponse = new UrlResponse(
            TEST_CODE,
            BASE_URL + "/r/" + TEST_CODE,
            TEST_LONG_URL,
            now,
            customExpiration
        );

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
            .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                        "longUrl": "https://www.example.com/very/long/path/to/resource",
                        "expiresAt": "%s"
                    }
                    """, customExpiration)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(TEST_CODE))
            .andExpect(jsonPath("$.expiresAt").exists());

        verify(urlService).createShortUrl(any(CreateUrlRequest.class));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when invalid URL format submitted")
    void shouldReturn400_WhenInvalidUrlSubmitted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": "not-a-valid-url"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).createShortUrl(any());
        verify(metricsService, never()).recordUrlCreated();
    }

    @Test
    @DisplayName("Should return 400 Bad Request when URL with unencoded spaces submitted")
    void shouldReturn400_WhenUrlWithSpacesSubmitted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": "https://example.com/path with spaces"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when empty body submitted")
    void shouldReturn400_WhenEmptyBodySubmitted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when longUrl is null")
    void shouldReturn400_WhenLongUrlIsNull() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when longUrl is blank")
    void shouldReturn400_WhenLongUrlIsBlank() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": "   "
                    }
                    """))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when URL exceeds max length")
    void shouldReturn400_WhenUrlTooLong() throws Exception {
        // Given - URL longer than 2048 characters
        String veryLongUrl = "https://example.com/" + "a".repeat(2050);

        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                        "longUrl": "%s"
                    }
                    """, veryLongUrl)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when malformed JSON submitted")
    void shouldReturn400_WhenMalformedJsonSubmitted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when dangerous URL protocol submitted")
    void shouldReturn400_WhenDangerousProtocolSubmitted() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": "javascript:alert('xss')"
                    }
                    """))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).createShortUrl(any());
    }

    // ==================== GET /api/urls/{code} Tests ====================

    @Test
    @DisplayName("Should return 200 OK with metadata when valid code provided")
    void shouldReturn200WithMetadata_WhenValidCodeProvided() throws Exception {
        // Given
        Instant now = Instant.now();
        Instant expiresAt = now.plus(365, ChronoUnit.DAYS);

        UrlMetadataResponse mockResponse = new UrlMetadataResponse(
            TEST_CODE,
            TEST_LONG_URL,
            BASE_URL + "/r/" + TEST_CODE,
            now,
            expiresAt,
            42L
        );

        when(urlService.getUrlMetadata(TEST_CODE))
            .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/urls/" + TEST_CODE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(TEST_CODE))
            .andExpect(jsonPath("$.longUrl").value(TEST_LONG_URL))
            .andExpect(jsonPath("$.shortUrl").value(BASE_URL + "/r/" + TEST_CODE))
            .andExpect(jsonPath("$.hitCount").value(42))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.expiresAt").exists());

        verify(urlService).getUrlMetadata(TEST_CODE);
        verify(metricsService).recordUrlLookup();
    }

    @Test
    @DisplayName("Should return 404 Not Found when code does not exist")
    void shouldReturn404_WhenCodeNotFound() throws Exception {
        // Given
        when(urlService.getUrlMetadata(anyString()))
            .thenThrow(new UrlNotFoundException("Short code not found: nonexistent"));

        // When & Then
        mockMvc.perform(get("/api/urls/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").value(containsString("not found")));

        verify(urlService).getUrlMetadata("nonexistent");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code has invalid format")
    void shouldReturn400_WhenInvalidCodeFormat() throws Exception {
        // When & Then - code with special characters
        mockMvc.perform(get("/api/urls/abc@123"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).getUrlMetadata(anyString());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code is too short")
    void shouldReturn400_WhenCodeTooShort() throws Exception {
        // When & Then - code with only 3 characters
        mockMvc.perform(get("/api/urls/abc"))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).getUrlMetadata(anyString());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code is too long")
    void shouldReturn400_WhenCodeTooLong() throws Exception {
        // When & Then - code with more than 12 characters
        mockMvc.perform(get("/api/urls/abcdefghijklm"))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).getUrlMetadata(anyString());
    }

    // ==================== DELETE /api/urls/{code} Tests ====================

    @Test
    @DisplayName("Should return 204 No Content when URL successfully deleted")
    void shouldReturn204_WhenUrlSuccessfullyDeleted() throws Exception {
        // Given
        doNothing().when(urlService).deleteShortUrl(TEST_CODE);

        // When & Then
        mockMvc.perform(delete("/api/urls/" + TEST_CODE))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(urlService).deleteShortUrl(TEST_CODE);
        verify(metricsService).recordUrlDeleted();
    }

    @Test
    @DisplayName("Should return 404 Not Found when deleting non-existent code")
    void shouldReturn404_WhenDeletingNonExistentCode() throws Exception {
        // Given
        doThrow(new UrlNotFoundException("Short code not found: nonexistent"))
            .when(urlService).deleteShortUrl(anyString());

        // When & Then
        mockMvc.perform(delete("/api/urls/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService).deleteShortUrl("nonexistent");
        verify(metricsService, never()).recordUrlDeleted();
    }

    @Test
    @DisplayName("Should return 400 Bad Request when deleting with invalid code format")
    void shouldReturn400_WhenDeletingInvalidCode() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/urls/invalid@code"))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).deleteShortUrl(anyString());
    }

    // ==================== PATCH /api/urls/{code}/expiration Tests ====================

    @Test
    @DisplayName("Should return 204 No Content when expiration updated successfully")
    void shouldReturn204_WhenExpirationUpdated() throws Exception {
        // Given
        Instant newExpiration = Instant.now().plus(60, ChronoUnit.DAYS);
        doNothing().when(urlService).updateExpiration(TEST_CODE, newExpiration);

        // When & Then
        mockMvc.perform(patch("/api/urls/" + TEST_CODE + "/expiration")
                .param("expiresAt", newExpiration.toString()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(urlService).updateExpiration(TEST_CODE, newExpiration);
    }

    @Test
    @DisplayName("Should return 204 No Content when setting expiration to null (permanent)")
    void shouldReturn204_WhenSettingExpirationToNull() throws Exception {
        // Given
        doNothing().when(urlService).updateExpiration(TEST_CODE, null);

        // When & Then
        mockMvc.perform(patch("/api/urls/" + TEST_CODE + "/expiration"))
            .andExpect(status().isNoContent());

        verify(urlService).updateExpiration(TEST_CODE, null);
    }

    @Test
    @DisplayName("Should return 404 Not Found when updating expiration for non-existent code")
    void shouldReturn404_WhenUpdatingNonExistentCode() throws Exception {
        // Given
        Instant newExpiration = Instant.now().plus(60, ChronoUnit.DAYS);
        doThrow(new UrlNotFoundException("Short code not found: nonexistent"))
            .when(urlService).updateExpiration(anyString(), any());

        // When & Then
        mockMvc.perform(patch("/api/urls/nonexistent/expiration")
                .param("expiresAt", newExpiration.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService).updateExpiration("nonexistent", newExpiration);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when updating with invalid code format")
    void shouldReturn400_WhenUpdatingWithInvalidCode() throws Exception {
        // When & Then
        mockMvc.perform(patch("/api/urls/invalid@code/expiration")
                .param("expiresAt", Instant.now().toString()))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).updateExpiration(anyString(), any());
    }

    // ==================== Content Type Tests ====================

    @Test
    @DisplayName("Should return 415 Unsupported Media Type when wrong content type used")
    void shouldReturn415_WhenWrongContentType() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
            .andExpect(status().isUnsupportedMediaType());

        verify(urlService, never()).createShortUrl(any());
    }

    @Test
    @DisplayName("Should accept application/json content type")
    void shouldAcceptJsonContentType() throws Exception {
        // Given
        UrlResponse mockResponse = new UrlResponse(
            TEST_CODE,
            BASE_URL + "/r/" + TEST_CODE,
            TEST_LONG_URL,
            Instant.now(),
            Instant.now().plus(365, ChronoUnit.DAYS)
        );

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
            .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "longUrl": "https://www.example.com/test"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Content-Type", containsString("application/json")));

        verify(urlService).createShortUrl(any(CreateUrlRequest.class));
    }
}
