package com.urlshortener.repository;

import com.urlshortener.model.UrlMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UrlMappingRepository using @DataJpaTest.
 * Tests JPA repository methods with an in-memory H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UrlMappingRepository Integration Tests")
class UrlMappingRepositoryTest {

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final String TEST_CODE = "abc123d";
    private static final String TEST_LONG_URL = "https://www.example.com/test";

    @BeforeEach
    void setUp() {
        // Clean database before each test
        urlMappingRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    // ==================== Save and Find Tests ====================

    @Test
    @DisplayName("Should save and find URL mapping by code")
    void shouldSaveAndFindByCode() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);

        // When
        UrlMapping savedMapping = urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> foundMapping = urlMappingRepository.findByCode(TEST_CODE);

        // Then
        assertTrue(foundMapping.isPresent(), "Mapping should be found by code");
        assertEquals(savedMapping.getId(), foundMapping.get().getId());
        assertEquals(TEST_CODE, foundMapping.get().getCode());
        assertEquals(TEST_LONG_URL, foundMapping.get().getLongUrl());
    }

    @Test
    @DisplayName("Should save and find URL mapping by long URL")
    void shouldSaveAndFindByLongUrl() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);

        // When
        urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> foundMapping = urlMappingRepository.findByLongUrl(TEST_LONG_URL);

        // Then
        assertTrue(foundMapping.isPresent(), "Mapping should be found by long URL");
        assertEquals(TEST_CODE, foundMapping.get().getCode());
        assertEquals(TEST_LONG_URL, foundMapping.get().getLongUrl());
    }

    @Test
    @DisplayName("Should return empty when code not found")
    void shouldReturnEmpty_WhenCodeNotFound() {
        // When
        Optional<UrlMapping> foundMapping = urlMappingRepository.findByCode("nonexistent");

        // Then
        assertFalse(foundMapping.isPresent(), "Should return empty for non-existent code");
    }

    @Test
    @DisplayName("Should return empty when long URL not found")
    void shouldReturnEmpty_WhenLongUrlNotFound() {
        // When
        Optional<UrlMapping> foundMapping = urlMappingRepository.findByLongUrl("https://nonexistent.com");

        // Then
        assertFalse(foundMapping.isPresent(), "Should return empty for non-existent long URL");
    }

    // ==================== Unique Constraint Tests ====================

    @Test
    @DisplayName("Should enforce unique code constraint")
    void shouldEnforceUniqueCodeConstraint() {
        // Given
        UrlMapping mapping1 = createTestMapping(TEST_CODE, TEST_LONG_URL);
        UrlMapping mapping2 = createTestMapping(TEST_CODE, "https://different.com");

        // When
        urlMappingRepository.save(mapping1);
        entityManager.flush();

        // Then
        assertThrows(
            DataIntegrityViolationException.class,
            () -> {
                urlMappingRepository.save(mapping2);
                entityManager.flush();
            },
            "Should throw exception when saving duplicate code"
        );
    }

    @Test
    @DisplayName("Should allow same long URL with different codes")
    void shouldAllowSameLongUrlWithDifferentCodes() {
        // Given
        UrlMapping mapping1 = createTestMapping("code1", TEST_LONG_URL);
        UrlMapping mapping2 = createTestMapping("code2", TEST_LONG_URL);

        // When & Then
        assertDoesNotThrow(() -> {
            urlMappingRepository.save(mapping1);
            urlMappingRepository.save(mapping2);
            entityManager.flush();
        }, "Should allow same long URL with different codes");

        assertEquals(2, urlMappingRepository.count());
    }

    // ==================== Exists Tests ====================

    @Test
    @DisplayName("Should check if code exists")
    void shouldCheckIfCodeExists() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);
        urlMappingRepository.save(mapping);
        entityManager.flush();

        // When
        boolean exists = urlMappingRepository.existsByCode(TEST_CODE);
        boolean notExists = urlMappingRepository.existsByCode("nonexistent");

        // Then
        assertTrue(exists, "Should return true for existing code");
        assertFalse(notExists, "Should return false for non-existent code");
    }

    // ==================== Expiration Tests ====================

    @Test
    @DisplayName("Should find expired URLs")
    void shouldFindExpiredUrls() {
        // Given
        Instant now = Instant.now();

        // Expired URL
        UrlMapping expiredMapping = createTestMapping("expired", TEST_LONG_URL);
        expiredMapping.setExpiresAt(now.minus(1, ChronoUnit.DAYS));

        // Active URL
        UrlMapping activeMapping = createTestMapping("active", "https://active.com");
        activeMapping.setExpiresAt(now.plus(1, ChronoUnit.DAYS));

        // Permanent URL (no expiration)
        UrlMapping permanentMapping = createTestMapping("permanent", "https://permanent.com");
        permanentMapping.setExpiresAt(null);

        urlMappingRepository.save(expiredMapping);
        urlMappingRepository.save(activeMapping);
        urlMappingRepository.save(permanentMapping);
        entityManager.flush();

        // When
        List<UrlMapping> expiredUrls = urlMappingRepository.findExpiredUrls(now);

        // Then
        assertEquals(1, expiredUrls.size(), "Should find only expired URL");
        assertEquals("expired", expiredUrls.get(0).getCode());
    }

    @Test
    @DisplayName("Should delete expired URLs")
    void shouldDeleteExpiredUrls() {
        // Given
        Instant now = Instant.now();

        UrlMapping expired1 = createTestMapping("expired1", "https://expired1.com");
        expired1.setExpiresAt(now.minus(1, ChronoUnit.DAYS));

        UrlMapping expired2 = createTestMapping("expired2", "https://expired2.com");
        expired2.setExpiresAt(now.minus(2, ChronoUnit.DAYS));

        UrlMapping active = createTestMapping("active", "https://active.com");
        active.setExpiresAt(now.plus(1, ChronoUnit.DAYS));

        urlMappingRepository.save(expired1);
        urlMappingRepository.save(expired2);
        urlMappingRepository.save(active);
        entityManager.flush();
        entityManager.clear();

        // When
        int deletedCount = urlMappingRepository.deleteExpiredUrls(now);
        entityManager.flush();

        // Then
        assertEquals(2, deletedCount, "Should delete 2 expired URLs");
        assertEquals(1, urlMappingRepository.count(), "Should have 1 URL remaining");

        Optional<UrlMapping> remaining = urlMappingRepository.findByCode("active");
        assertTrue(remaining.isPresent(), "Active URL should remain");
    }

    @Test
    @DisplayName("Should count active URLs correctly")
    void shouldCountActiveUrls() {
        // Given
        Instant now = Instant.now();

        // 2 expired URLs
        UrlMapping expired1 = createTestMapping("expired1", "https://expired1.com");
        expired1.setExpiresAt(now.minus(1, ChronoUnit.DAYS));

        UrlMapping expired2 = createTestMapping("expired2", "https://expired2.com");
        expired2.setExpiresAt(now.minus(2, ChronoUnit.DAYS));

        // 3 active URLs
        UrlMapping active1 = createTestMapping("active1", "https://active1.com");
        active1.setExpiresAt(now.plus(1, ChronoUnit.DAYS));

        UrlMapping active2 = createTestMapping("active2", "https://active2.com");
        active2.setExpiresAt(now.plus(2, ChronoUnit.DAYS));

        UrlMapping permanent = createTestMapping("permanent", "https://permanent.com");
        permanent.setExpiresAt(null);

        urlMappingRepository.saveAll(List.of(expired1, expired2, active1, active2, permanent));
        entityManager.flush();

        // When
        long activeCount = urlMappingRepository.countActiveUrls(now);

        // Then
        assertEquals(3, activeCount, "Should count 3 active URLs (2 with future expiry + 1 permanent)");
    }

    // ==================== Hit Count Tests ====================

    @Test
    @DisplayName("Should increment hit count atomically")
    void shouldIncrementHitCount() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);
        mapping.setHitCount(5L);
        urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        // When
        int updated = urlMappingRepository.incrementHitCount(TEST_CODE);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertEquals(1, updated, "Should return 1 row updated");

        Optional<UrlMapping> updatedMapping = urlMappingRepository.findByCode(TEST_CODE);
        assertTrue(updatedMapping.isPresent());
        assertEquals(6, updatedMapping.get().getHitCount(), "Hit count should be incremented");
    }

    @Test
    @DisplayName("Should increment hit count multiple times")
    void shouldIncrementHitCountMultipleTimes() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);
        mapping.setHitCount(0L);
        urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        // When - Increment 10 times
        for (int i = 0; i < 10; i++) {
            urlMappingRepository.incrementHitCount(TEST_CODE);
            entityManager.flush();
        }
        entityManager.clear();

        // Then
        Optional<UrlMapping> updatedMapping = urlMappingRepository.findByCode(TEST_CODE);
        assertTrue(updatedMapping.isPresent());
        assertEquals(10, updatedMapping.get().getHitCount(), "Hit count should be 10");
    }

    @Test
    @DisplayName("Should return zero when incrementing non-existent code")
    void shouldReturnZero_WhenIncrementingNonExistentCode() {
        // When
        int updated = urlMappingRepository.incrementHitCount("nonexistent");

        // Then
        assertEquals(0, updated, "Should return 0 when code doesn't exist");
    }

    // ==================== Index Tests (Performance) ====================

    @Test
    @DisplayName("Should efficiently query by indexed code column")
    void shouldEfficientlyQueryByCode() {
        // Given - Create many mappings
        for (int i = 0; i < 100; i++) {
            UrlMapping mapping = createTestMapping("code" + i, "https://url" + i + ".com");
            urlMappingRepository.save(mapping);
        }
        entityManager.flush();
        entityManager.clear();

        // When
        long startTime = System.nanoTime();
        Optional<UrlMapping> found = urlMappingRepository.findByCode("code50");
        long endTime = System.nanoTime();

        // Then
        assertTrue(found.isPresent(), "Should find mapping");
        long durationMs = (endTime - startTime) / 1_000_000;
        assertTrue(durationMs < 100, "Query should be fast with index (< 100ms)");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle URL with special characters")
    void shouldHandleUrlWithSpecialCharacters() {
        // Given
        String urlWithSpecialChars = "https://example.com/search?q=hello+world&lang=en#section";
        UrlMapping mapping = createTestMapping(TEST_CODE, urlWithSpecialChars);

        // When
        UrlMapping savedMapping = urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> foundMapping = urlMappingRepository.findByCode(TEST_CODE);

        // Then
        assertTrue(foundMapping.isPresent());
        assertEquals(urlWithSpecialChars, foundMapping.get().getLongUrl());
    }

    @Test
    @DisplayName("Should handle very long URLs")
    void shouldHandleVeryLongUrls() {
        // Given
        String veryLongUrl = "https://example.com/" + "a".repeat(2000);
        UrlMapping mapping = createTestMapping(TEST_CODE, veryLongUrl);

        // When
        UrlMapping savedMapping = urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> foundMapping = urlMappingRepository.findByCode(TEST_CODE);

        // Then
        assertTrue(foundMapping.isPresent());
        assertEquals(veryLongUrl, foundMapping.get().getLongUrl());
    }

    @Test
    @DisplayName("Should handle null expiration (permanent URLs)")
    void shouldHandleNullExpiration() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);
        mapping.setExpiresAt(null);

        // When
        UrlMapping savedMapping = urlMappingRepository.save(mapping);
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> foundMapping = urlMappingRepository.findByCode(TEST_CODE);

        // Then
        assertTrue(foundMapping.isPresent());
        assertNull(foundMapping.get().getExpiresAt(), "Expiration should be null");
    }

    @Test
    @DisplayName("Should handle deletion")
    void shouldHandleDeletion() {
        // Given
        UrlMapping mapping = createTestMapping(TEST_CODE, TEST_LONG_URL);
        urlMappingRepository.save(mapping);
        entityManager.flush();

        // When
        urlMappingRepository.delete(mapping);
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> foundMapping = urlMappingRepository.findByCode(TEST_CODE);

        // Then
        assertFalse(foundMapping.isPresent(), "Mapping should be deleted");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test UrlMapping with default values.
     *
     * @param code    short code
     * @param longUrl long URL
     * @return UrlMapping instance
     */
    private UrlMapping createTestMapping(String code, String longUrl) {
        return UrlMapping.builder()
            .code(code)
            .longUrl(longUrl)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(0L)
            .build();
    }
}
