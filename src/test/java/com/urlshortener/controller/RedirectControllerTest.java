package com.urlshortener.controller;

import com.urlshortener.exception.Rfc7807GlobalExceptionHandler;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.observability.UrlMetricsService;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.function.Supplier;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for RedirectController using @WebMvcTest.
 * Tests redirect functionality in isolation with mocked service layer.
 */
@WebMvcTest(controllers = RedirectController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    },
    properties = {
        "urlshortener.rate-limit.enabled=false"
    })
@org.springframework.test.context.ActiveProfiles("test")
@Import(Rfc7807GlobalExceptionHandler.class)
@DisplayName("RedirectController Slice Tests")
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @MockBean
    private UrlMetricsService metricsService;

    private static final String TEST_CODE = "abc123d";
    private static final String TEST_LONG_URL = "https://www.example.com/original";

    // ==================== Successful Redirect Tests ====================

    @Test
    @DisplayName("Should return 302 Found when valid code provided")
    void shouldReturn302_WhenValidCodeProvided() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL))
            .andExpect(content().string(""));

        verify(urlService).resolveShortUrl(TEST_CODE);
        verify(metricsService).recordRedirectTotal();
        verify(metricsService).timeRedirect(any());
        verify(metricsService).recordRedirectSuccess();
    }

    @Test
    @DisplayName("Should redirect to URL with query parameters")
    void shouldRedirectToUrlWithQueryParameters() throws Exception {
        // Given
        String urlWithParams = "https://example.com/search?q=test&category=all";
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(urlWithParams);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", urlWithParams));

        verify(urlService).resolveShortUrl(TEST_CODE);
        verify(metricsService).recordRedirectSuccess();
    }

    @Test
    @DisplayName("Should redirect to URL with fragment")
    void shouldRedirectToUrlWithFragment() throws Exception {
        // Given
        String urlWithFragment = "https://example.com/docs#section-3";
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(urlWithFragment);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", urlWithFragment));

        verify(urlService).resolveShortUrl(TEST_CODE);
    }

    @Test
    @DisplayName("Should redirect to URL with port number")
    void shouldRedirectToUrlWithPort() throws Exception {
        // Given
        String urlWithPort = "https://example.com:8443/api/resource";
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(urlWithPort);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", urlWithPort));
    }

    @Test
    @DisplayName("Should redirect to internationalized domain")
    void shouldRedirectToInternationalDomain() throws Exception {
        // Given
        String internationalUrl = "https://münchen.de/page";
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(internationalUrl);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", internationalUrl));
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should return 404 Not Found when code does not exist")
    void shouldReturn404_WhenCodeNotFound() throws Exception {
        // Given
        when(urlService.resolveShortUrl(anyString()))
            .thenThrow(new UrlNotFoundException("Short code not found: nonexistent"));
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                try {
                    return supplier.get();
                } catch (RuntimeException e) {
                    throw e;
                }
            });

        // When & Then
        mockMvc.perform(get("/r/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService).resolveShortUrl("nonexistent");
        verify(metricsService).recordRedirectTotal();
        verify(metricsService).recordRedirectNotFound();
        verify(metricsService, never()).recordRedirectSuccess();
    }

    @Test
    @DisplayName("Should return 410 Gone when URL has expired")
    void shouldReturn410_WhenUrlExpired() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenThrow(new UrlExpiredException("URL has expired: " + TEST_CODE));
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                try {
                    return supplier.get();
                } catch (RuntimeException e) {
                    throw e;
                }
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService).resolveShortUrl(TEST_CODE);
        verify(metricsService).recordRedirectTotal();
        verify(metricsService).recordRedirectExpired();
        verify(metricsService, never()).recordRedirectSuccess();
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code has invalid format")
    void shouldReturn400_WhenInvalidCodeFormat() throws Exception {
        // When & Then - code with special characters
        mockMvc.perform(get("/r/abc@123"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.detail").exists());

        verify(urlService, never()).resolveShortUrl(anyString());
        verify(metricsService, never()).recordRedirectTotal();
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code is too short")
    void shouldReturn400_WhenCodeTooShort() throws Exception {
        // When & Then - code with only 3 characters
        mockMvc.perform(get("/r/abc"))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).resolveShortUrl(anyString());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code is too long")
    void shouldReturn400_WhenCodeTooLong() throws Exception {
        // When & Then - code with more than 12 characters
        mockMvc.perform(get("/r/abcdefghijklm"))
            .andExpect(status().isBadRequest());

        verify(urlService, never()).resolveShortUrl(anyString());
    }

    // ==================== Request Header Tests ====================

    @Test
    @DisplayName("Should handle X-Forwarded-For header")
    void shouldHandleXForwardedForHeader() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE)
                .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(TEST_CODE);
        verify(metricsService).recordRedirectSuccess();
    }

    @Test
    @DisplayName("Should handle X-Real-IP header")
    void shouldHandleXRealIpHeader() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE)
                .header("X-Real-IP", "203.0.113.45"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(TEST_CODE);
    }

    @Test
    @DisplayName("Should handle User-Agent header")
    void shouldHandleUserAgentHeader() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + TEST_CODE)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(TEST_CODE);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle code with mixed case")
    void shouldHandleCodeWithMixedCase() throws Exception {
        // Given
        String mixedCaseCode = "AbC123D";
        when(urlService.resolveShortUrl(mixedCaseCode))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + mixedCaseCode))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(mixedCaseCode);
    }

    @Test
    @DisplayName("Should handle code with all uppercase")
    void shouldHandleCodeWithUppercase() throws Exception {
        // Given
        String upperCode = "ABC123D";
        when(urlService.resolveShortUrl(upperCode))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + upperCode))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(upperCode);
    }

    @Test
    @DisplayName("Should handle code with all numbers")
    void shouldHandleCodeWithAllNumbers() throws Exception {
        // Given
        String numericCode = "1234567";
        when(urlService.resolveShortUrl(numericCode))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + numericCode))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(numericCode);
    }

    @Test
    @DisplayName("Should handle shortest valid code (4 characters)")
    void shouldHandleShortestValidCode() throws Exception {
        // Given
        String shortCode = "abcd";
        when(urlService.resolveShortUrl(shortCode))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + shortCode))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(shortCode);
    }

    @Test
    @DisplayName("Should handle longest valid code (12 characters)")
    void shouldHandleLongestValidCode() throws Exception {
        // Given
        String longCode = "abc123def456";
        when(urlService.resolveShortUrl(longCode))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When & Then
        mockMvc.perform(get("/r/" + longCode))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", TEST_LONG_URL));

        verify(urlService).resolveShortUrl(longCode);
    }

    // ==================== Health Check Tests ====================

    @Test
    @DisplayName("Should return 200 OK for health check endpoint")
    void shouldReturn200_ForHealthCheck() throws Exception {
        // When & Then
        mockMvc.perform(get("/r/health"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(urlService, never()).resolveShortUrl(anyString());
        verify(metricsService, never()).recordRedirectTotal();
    }

    // ==================== Performance and Metrics Tests ====================

    @Test
    @DisplayName("Should record redirect metrics for successful redirect")
    void shouldRecordMetrics_ForSuccessfulRedirect() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenReturn(TEST_LONG_URL);
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });

        // When
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isFound());

        // Then - verify all metrics were recorded
        verify(metricsService).recordRedirectTotal();
        verify(metricsService).timeRedirect(any());
        verify(metricsService).recordRedirectSuccess();
        verify(metricsService, never()).recordRedirectNotFound();
        verify(metricsService, never()).recordRedirectExpired();
    }

    @Test
    @DisplayName("Should record redirect metrics for not found")
    void shouldRecordMetrics_ForNotFound() throws Exception {
        // Given
        when(urlService.resolveShortUrl(anyString()))
            .thenThrow(new UrlNotFoundException("Not found"));
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                try {
                    return supplier.get();
                } catch (RuntimeException e) {
                    throw e;
                }
            });

        // When
        mockMvc.perform(get("/r/nonexistent"))
            .andExpect(status().isNotFound());

        // Then - verify correct metrics were recorded
        verify(metricsService).recordRedirectTotal();
        verify(metricsService).timeRedirect(any());
        verify(metricsService).recordRedirectNotFound();
        verify(metricsService, never()).recordRedirectSuccess();
        verify(metricsService, never()).recordRedirectExpired();
    }

    @Test
    @DisplayName("Should record redirect metrics for expired URL")
    void shouldRecordMetrics_ForExpired() throws Exception {
        // Given
        when(urlService.resolveShortUrl(TEST_CODE))
            .thenThrow(new UrlExpiredException("Expired"));
        when(metricsService.timeRedirect(any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                try {
                    return supplier.get();
                } catch (RuntimeException e) {
                    throw e;
                }
            });

        // When
        mockMvc.perform(get("/r/" + TEST_CODE))
            .andExpect(status().isGone());

        // Then - verify correct metrics were recorded
        verify(metricsService).recordRedirectTotal();
        verify(metricsService).timeRedirect(any());
        verify(metricsService).recordRedirectExpired();
        verify(metricsService, never()).recordRedirectSuccess();
        verify(metricsService, never()).recordRedirectNotFound();
    }
}
