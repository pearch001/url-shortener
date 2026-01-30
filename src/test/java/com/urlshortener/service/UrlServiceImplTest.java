package com.urlshortener.service;

import com.urlshortener.config.UrlShortenerProperties;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlMetadataResponse;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.observability.UrlMetricsService;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.CodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for UrlServiceImpl.
 * Tests business logic for URL creation, resolution, metadata, and deletion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UrlService Unit Tests")
class UrlServiceImplTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private CodeGenerator codeGenerator;

    @Mock
    private UrlShortenerProperties properties;

    @Mock
    private UrlMetricsService metricsService;

    @InjectMocks
    private UrlServiceImpl urlService;

    @Captor
    private ArgumentCaptor<UrlMapping> urlMappingCaptor;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_CODE = "abc123d";
    private static final String TEST_LONG_URL = "https://www.example.com/very/long/path/to/resource";

    @BeforeEach
    void setUp() {
        // Configure mock properties with lenient stubbing (not all tests use these)
        lenient().when(properties.getBaseUrl()).thenReturn(BASE_URL);
        lenient().when(properties.getUrl()).thenReturn(new UrlShortenerProperties.Url());
        lenient().when(properties.buildShortUrl(anyString()))
            .thenAnswer(invocation -> BASE_URL + "/r/" + invocation.getArgument(0));
    }

    // ==================== Create Short URL Tests ====================

    @Test
    @DisplayName("Should create short URL when valid request provided")
    void shouldCreateShortUrl_WhenValidRequest() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest(
            TEST_LONG_URL,
            Instant.now().plus(30, ChronoUnit.DAYS)
        );

        when(urlMappingRepository.findByLongUrl(TEST_LONG_URL)).thenReturn(Optional.empty());
        when(codeGenerator.generateUniqueCode(eq(TEST_LONG_URL), any())).thenReturn(TEST_CODE);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setCreatedAt(Instant.now());
            return mapping;
        });

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response, "Response should not be null");
        assertEquals(TEST_CODE, response.code(), "Code should match generated code");
        assertEquals(TEST_LONG_URL, response.longUrl(), "Long URL should match request");
        assertEquals(BASE_URL + "/r/" + TEST_CODE, response.shortUrl(), "Short URL should be properly formatted");
        assertNotNull(response.createdAt(), "Created timestamp should be set");
        assertNotNull(response.expiresAt(), "Expiration timestamp should be set");

        // Verify interactions
        verify(urlMappingRepository).findByLongUrl(TEST_LONG_URL);
        verify(codeGenerator).generateUniqueCode(eq(TEST_LONG_URL), any());
        verify(urlMappingRepository).save(urlMappingCaptor.capture());

        UrlMapping savedMapping = urlMappingCaptor.getValue();
        assertEquals(TEST_CODE, savedMapping.getCode());
        assertEquals(TEST_LONG_URL, savedMapping.getLongUrl());
        assertEquals(0, savedMapping.getHitCount());
    }

    @Test
    @DisplayName("Should return existing code when URL already exists (idempotent)")
    void shouldReturnExistingCode_WhenUrlAlreadyExists() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest(TEST_LONG_URL, null);

        UrlMapping existingMapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(42L)
            .build();

        when(urlMappingRepository.findByLongUrl(TEST_LONG_URL))
            .thenReturn(Optional.of(existingMapping));

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertEquals(TEST_CODE, response.code(), "Should return existing code");
        assertEquals(TEST_LONG_URL, response.longUrl(), "Long URL should match");
        assertEquals(existingMapping.getCreatedAt(), response.createdAt(),
            "Should return original creation time");

        // Verify code generator was NOT called (idempotent)
        verify(codeGenerator, never()).generateUniqueCode(any(), any());
        verify(urlMappingRepository, never()).save(any());
        verify(urlMappingRepository).findByLongUrl(TEST_LONG_URL);
    }

    @Test
    @DisplayName("Should create new URL when existing URL is expired")
    void shouldCreateNewUrl_WhenExistingUrlIsExpired() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest(TEST_LONG_URL, null);

        UrlMapping expiredMapping = UrlMapping.builder()
            .id(1L)
            .code("oldCode")
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now().minus(100, ChronoUnit.DAYS))
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired
            .hitCount(10L)
            .build();

        when(urlMappingRepository.findByLongUrl(TEST_LONG_URL))
            .thenReturn(Optional.of(expiredMapping));
        when(codeGenerator.generateUniqueCode(eq(TEST_LONG_URL), any())).thenReturn(TEST_CODE);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setId(2L);
            return mapping;
        });

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertEquals(TEST_CODE, response.code(), "Should generate new code for expired URL");
        assertNotEquals("oldCode", response.code(), "Should not reuse expired code");

        // Verify new code was generated and saved
        verify(codeGenerator).generateUniqueCode(eq(TEST_LONG_URL), any());
        verify(urlMappingRepository).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should handle collision retry during code generation")
    void shouldHandleCollisionRetry_WhenCodeExists() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest(TEST_LONG_URL, null);

        when(urlMappingRepository.findByLongUrl(TEST_LONG_URL)).thenReturn(Optional.empty());
        // Code generator handles collision internally, we just need to return a code
        when(codeGenerator.generateUniqueCode(eq(TEST_LONG_URL), any())).thenReturn(TEST_CODE);

        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setCreatedAt(Instant.now());
            return mapping;
        });

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertEquals(TEST_CODE, response.code());
        verify(codeGenerator).generateUniqueCode(eq(TEST_LONG_URL), any());
    }

    @Test
    @DisplayName("Should normalize URL before creating short code")
    void shouldNormalizeUrl_BeforeCreating() {
        // Given
        String urlWithSpaces = "  " + TEST_LONG_URL + "  ";
        CreateUrlRequest request = new CreateUrlRequest(urlWithSpaces, null);

        when(urlMappingRepository.findByLongUrl(TEST_LONG_URL)).thenReturn(Optional.empty());
        when(codeGenerator.generateUniqueCode(eq(TEST_LONG_URL), any())).thenReturn(TEST_CODE);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setCreatedAt(Instant.now());
            return mapping;
        });

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertEquals(TEST_LONG_URL, response.longUrl(), "URL should be trimmed/normalized");
        verify(urlMappingRepository).findByLongUrl(TEST_LONG_URL);
    }

    // ==================== Resolve Short URL Tests ====================

    @Test
    @DisplayName("Should resolve short URL when code exists and not expired")
    void shouldResolveShortUrl_WhenCodeExists() {
        // Given
        UrlMapping mapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(10L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(mapping));
        when(urlMappingRepository.incrementHitCount(TEST_CODE)).thenReturn(1);

        // When
        String resolvedUrl = urlService.resolveShortUrl(TEST_CODE);

        // Then
        assertEquals(TEST_LONG_URL, resolvedUrl, "Should return correct long URL");
        verify(urlMappingRepository).findByCode(TEST_CODE);
        verify(urlMappingRepository).incrementHitCount(TEST_CODE);
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException when code does not exist")
    void shouldThrowNotFoundException_WhenCodeDoesNotExist() {
        // Given
        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.empty());

        // When & Then
        UrlNotFoundException exception = assertThrows(
            UrlNotFoundException.class,
            () -> urlService.resolveShortUrl(TEST_CODE),
            "Should throw UrlNotFoundException"
        );

        assertTrue(exception.getMessage().contains(TEST_CODE),
            "Exception message should contain the code");
        verify(urlMappingRepository).findByCode(TEST_CODE);
        verify(urlMappingRepository, never()).incrementHitCount(any());
    }

    @Test
    @DisplayName("Should throw UrlExpiredException when URL is expired")
    void shouldThrowExpiredException_WhenUrlExpired() {
        // Given
        UrlMapping expiredMapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now().minus(100, ChronoUnit.DAYS))
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired
            .hitCount(50L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(expiredMapping));

        // When & Then
        UrlExpiredException exception = assertThrows(
            UrlExpiredException.class,
            () -> urlService.resolveShortUrl(TEST_CODE),
            "Should throw UrlExpiredException"
        );

        assertTrue(exception.getMessage().contains(TEST_CODE),
            "Exception message should contain the code");
        assertTrue(exception.getMessage().contains("expired"),
            "Exception message should mention expiration");

        verify(urlMappingRepository).findByCode(TEST_CODE);
        verify(urlMappingRepository, never()).incrementHitCount(any());
    }

    @Test
    @DisplayName("Should increment hit count on each resolve")
    void shouldIncrementHitCount_OnEachResolve() {
        // Given
        UrlMapping mapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(5L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(mapping));
        when(urlMappingRepository.incrementHitCount(TEST_CODE)).thenReturn(1);

        // When - Resolve multiple times
        urlService.resolveShortUrl(TEST_CODE);
        urlService.resolveShortUrl(TEST_CODE);
        urlService.resolveShortUrl(TEST_CODE);

        // Then
        verify(urlMappingRepository, times(3)).findByCode(TEST_CODE);
        verify(urlMappingRepository, times(3)).incrementHitCount(TEST_CODE);
    }

    @Test
    @DisplayName("Should handle URL with null expiration (permanent)")
    void shouldHandleUrlWithNullExpiration() {
        // Given
        UrlMapping permanentMapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now())
            .expiresAt(null) // Permanent
            .hitCount(0L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(permanentMapping));
        when(urlMappingRepository.incrementHitCount(TEST_CODE)).thenReturn(1);

        // When
        String resolvedUrl = urlService.resolveShortUrl(TEST_CODE);

        // Then
        assertEquals(TEST_LONG_URL, resolvedUrl, "Should resolve permanent URL");
        verify(urlMappingRepository).incrementHitCount(TEST_CODE);
    }

    // ==================== Get URL Metadata Tests ====================

    @Test
    @DisplayName("Should get URL metadata when code exists")
    void shouldGetUrlMetadata_WhenCodeExists() {
        // Given
        Instant createdAt = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().plus(20, ChronoUnit.DAYS);

        UrlMapping mapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(createdAt)
            .expiresAt(expiresAt)
            .hitCount(42L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(mapping));

        // When
        UrlMetadataResponse metadata = urlService.getUrlMetadata(TEST_CODE);

        // Then
        assertNotNull(metadata, "Metadata should not be null");
        assertEquals(TEST_CODE, metadata.code(), "Code should match");
        assertEquals(TEST_LONG_URL, metadata.longUrl(), "Long URL should match");
        assertEquals(BASE_URL + "/r/" + TEST_CODE, metadata.shortUrl(), "Short URL should be formatted");
        assertEquals(createdAt, metadata.createdAt(), "Created timestamp should match");
        assertEquals(expiresAt, metadata.expiresAt(), "Expiration timestamp should match");
        assertEquals(42, metadata.hitCount(), "Hit count should match");

        verify(urlMappingRepository).findByCode(TEST_CODE);
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException when getting metadata for non-existent code")
    void shouldThrowNotFoundException_WhenGettingMetadataForNonExistentCode() {
        // Given
        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            UrlNotFoundException.class,
            () -> urlService.getUrlMetadata(TEST_CODE),
            "Should throw UrlNotFoundException"
        );

        verify(urlMappingRepository).findByCode(TEST_CODE);
    }

    @Test
    @DisplayName("Should return metadata for expired URL (metadata retrieval does not check expiration)")
    void shouldReturnMetadata_ForExpiredUrl() {
        // Given
        UrlMapping expiredMapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now().minus(100, ChronoUnit.DAYS))
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .hitCount(100L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(expiredMapping));

        // When
        UrlMetadataResponse metadata = urlService.getUrlMetadata(TEST_CODE);

        // Then - Should return metadata even if expired
        assertNotNull(metadata, "Should return metadata for expired URL");
        assertEquals(TEST_CODE, metadata.code());
        assertEquals(100, metadata.hitCount());
    }

    // ==================== Delete Short URL Tests ====================

    @Test
    @DisplayName("Should delete short URL when code exists")
    void shouldDeleteShortUrl_WhenCodeExists() {
        // Given
        UrlMapping mapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(10L)
            .build();

        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(mapping));
        doNothing().when(urlMappingRepository).delete(mapping);

        // When
        assertDoesNotThrow(() -> urlService.deleteShortUrl(TEST_CODE));

        // Then
        verify(urlMappingRepository).findByCode(TEST_CODE);
        verify(urlMappingRepository).delete(mapping);
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException when deleting non-existent code")
    void shouldThrowNotFoundException_WhenDeletingNonExistentCode() {
        // Given
        when(urlMappingRepository.findByCode(TEST_CODE)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(
            UrlNotFoundException.class,
            () -> urlService.deleteShortUrl(TEST_CODE),
            "Should throw UrlNotFoundException"
        );

        verify(urlMappingRepository).findByCode(TEST_CODE);
        verify(urlMappingRepository, never()).delete(any());
    }

    // ==================== Update Expiration Tests ====================

    @Test
    @DisplayName("Should update expiration when code exists")
    void shouldUpdateExpiration_WhenCodeExists() {
        // Given
        Instant newExpiration = Instant.now().plus(60, ChronoUnit.DAYS);

        UrlMapping mapping = UrlMapping.builder()
            .id(1L)
            .code(TEST_CODE)
            .longUrl(TEST_LONG_URL)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(5L)
            .build();

        when(urlMappingRepository.existsByCode(TEST_CODE)).thenReturn(true);
        when(urlMappingRepository.updateExpirationTime(TEST_CODE, newExpiration)).thenReturn(1);

        // When
        assertDoesNotThrow(() -> urlService.updateExpiration(TEST_CODE, newExpiration));

        // Then
        verify(urlMappingRepository).existsByCode(TEST_CODE);
        verify(urlMappingRepository).updateExpirationTime(TEST_CODE, newExpiration);
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException when updating expiration for non-existent code")
    void shouldThrowNotFoundException_WhenUpdatingExpirationForNonExistentCode() {
        // Given
        Instant newExpiration = Instant.now().plus(60, ChronoUnit.DAYS);
        when(urlMappingRepository.existsByCode(TEST_CODE)).thenReturn(false);

        // When & Then
        assertThrows(
            UrlNotFoundException.class,
            () -> urlService.updateExpiration(TEST_CODE, newExpiration),
            "Should throw UrlNotFoundException"
        );

        verify(urlMappingRepository).existsByCode(TEST_CODE);
        verify(urlMappingRepository, never()).updateExpirationTime(anyString(), any());
    }

    @Test
    @DisplayName("Should allow setting expiration to null (permanent)")
    void shouldAllowSettingExpirationToNull() {
        // Given
        when(urlMappingRepository.existsByCode(TEST_CODE)).thenReturn(true);
        when(urlMappingRepository.updateExpirationTime(TEST_CODE, null)).thenReturn(1);

        // When
        assertDoesNotThrow(() -> urlService.updateExpiration(TEST_CODE, null));

        // Then
        verify(urlMappingRepository).existsByCode(TEST_CODE);
        verify(urlMappingRepository).updateExpirationTime(TEST_CODE, null);
    }

    // ==================== Cleanup Expired URLs Tests ====================

    @Test
    @DisplayName("Should cleanup expired URLs")
    void shouldCleanupExpiredUrls() {
        // Given
        when(urlMappingRepository.deleteExpiredUrls(any(Instant.class))).thenReturn(15);

        // When
        int deletedCount = urlService.cleanupExpiredUrls();

        // Then
        assertEquals(15, deletedCount, "Should return number of deleted URLs");
        verify(urlMappingRepository).deleteExpiredUrls(any(Instant.class));
    }

    @Test
    @DisplayName("Should return zero when no expired URLs to cleanup")
    void shouldReturnZero_WhenNoExpiredUrlsToCleanup() {
        // Given
        when(urlMappingRepository.deleteExpiredUrls(any(Instant.class))).thenReturn(0);

        // When
        int deletedCount = urlService.cleanupExpiredUrls();

        // Then
        assertEquals(0, deletedCount, "Should return 0 when no URLs deleted");
        verify(urlMappingRepository).deleteExpiredUrls(any(Instant.class));
    }

    // ==================== Edge Cases and Error Handling Tests ====================

    @Test
    @DisplayName("Should handle repository exception gracefully")
    void shouldHandleRepositoryException() {
        // Given
        when(urlMappingRepository.findByCode(TEST_CODE))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        assertThrows(
            RuntimeException.class,
            () -> urlService.resolveShortUrl(TEST_CODE),
            "Should propagate repository exceptions"
        );
    }

    @Test
    @DisplayName("Should handle very long URLs correctly")
    void shouldHandleVeryLongUrls() {
        // Given
        String veryLongUrl = "https://www.example.com/" + "a".repeat(2000);
        CreateUrlRequest request = new CreateUrlRequest(veryLongUrl, null);

        when(urlMappingRepository.findByLongUrl(veryLongUrl)).thenReturn(Optional.empty());
        when(codeGenerator.generateUniqueCode(eq(veryLongUrl), any())).thenReturn(TEST_CODE);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setCreatedAt(Instant.now());
            return mapping;
        });

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertEquals(veryLongUrl, response.longUrl());
        verify(urlMappingRepository).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should handle concurrent URL creation for same URL")
    void shouldHandleConcurrentUrlCreation() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest(TEST_LONG_URL, null);

        // First call: URL doesn't exist
        when(urlMappingRepository.findByLongUrl(TEST_LONG_URL))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.empty());

        when(codeGenerator.generateUniqueCode(eq(TEST_LONG_URL), any())).thenReturn(TEST_CODE);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setCreatedAt(Instant.now());
            return mapping;
        });

        // When - Simulate concurrent requests
        UrlResponse response1 = urlService.createShortUrl(request);
        UrlResponse response2 = urlService.createShortUrl(request);

        // Then
        assertNotNull(response1);
        assertNotNull(response2);
        verify(urlMappingRepository, times(2)).findByLongUrl(TEST_LONG_URL);
    }
}
