package com.urlshortener;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.model.UrlMapping;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Test data factory for creating test objects.
 * Provides consistent test data across all test classes.
 */
public class TestDataFactory {

    private TestDataFactory() {
        // Utility class - prevent instantiation
    }

    // ==================== Common Test Data ====================

    public static final String DEFAULT_CODE = "abc123d";
    public static final String DEFAULT_LONG_URL = "https://www.example.com/test";
    public static final String BASE_URL = "http://localhost:8080";

    // ==================== UrlMapping Factory Methods ====================

    /**
     * Creates a default UrlMapping for testing.
     *
     * @return UrlMapping with default values
     */
    public static UrlMapping createDefaultUrlMapping() {
        return createUrlMapping(DEFAULT_CODE, DEFAULT_LONG_URL);
    }

    /**
     * Creates a UrlMapping with specified code and URL.
     *
     * @param code    short code
     * @param longUrl long URL
     * @return UrlMapping instance
     */
    public static UrlMapping createUrlMapping(String code, String longUrl) {
        return UrlMapping.builder()
            .code(code)
            .longUrl(longUrl)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(0L)
            .build();
    }

    /**
     * Creates an expired UrlMapping.
     *
     * @param code    short code
     * @param longUrl long URL
     * @return expired UrlMapping
     */
    public static UrlMapping createExpiredUrlMapping(String code, String longUrl) {
        return UrlMapping.builder()
            .code(code)
            .longUrl(longUrl)
            .createdAt(Instant.now().minus(100, ChronoUnit.DAYS))
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .hitCount(0L)
            .build();
    }

    /**
     * Creates a permanent UrlMapping (no expiration).
     *
     * @param code    short code
     * @param longUrl long URL
     * @return permanent UrlMapping
     */
    public static UrlMapping createPermanentUrlMapping(String code, String longUrl) {
        return UrlMapping.builder()
            .code(code)
            .longUrl(longUrl)
            .createdAt(Instant.now())
            .expiresAt(null)
            .hitCount(0L)
            .build();
    }

    /**
     * Creates a UrlMapping with custom expiration.
     *
     * @param code      short code
     * @param longUrl   long URL
     * @param expiresAt expiration timestamp
     * @return UrlMapping with custom expiration
     */
    public static UrlMapping createUrlMappingWithExpiration(String code, String longUrl, Instant expiresAt) {
        return UrlMapping.builder()
            .code(code)
            .longUrl(longUrl)
            .createdAt(Instant.now())
            .expiresAt(expiresAt)
            .hitCount(0L)
            .build();
    }

    /**
     * Creates a UrlMapping with custom hit count.
     *
     * @param code     short code
     * @param longUrl  long URL
     * @param hitCount hit count
     * @return UrlMapping with custom hit count
     */
    public static UrlMapping createUrlMappingWithHitCount(String code, String longUrl, long hitCount) {
        return UrlMapping.builder()
            .code(code)
            .longUrl(longUrl)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .hitCount(hitCount)
            .build();
    }

    // ==================== CreateUrlRequest Factory Methods ====================

    /**
     * Creates a default CreateUrlRequest.
     *
     * @return CreateUrlRequest with default values
     */
    public static CreateUrlRequest createDefaultUrlRequest() {
        return new CreateUrlRequest(DEFAULT_LONG_URL, null);
    }

    /**
     * Creates a CreateUrlRequest with specified URL.
     *
     * @param longUrl long URL
     * @return CreateUrlRequest instance
     */
    public static CreateUrlRequest createUrlRequest(String longUrl) {
        return new CreateUrlRequest(longUrl, null);
    }

    /**
     * Creates a CreateUrlRequest with expiration.
     *
     * @param longUrl   long URL
     * @param expiresAt expiration timestamp
     * @return CreateUrlRequest with expiration
     */
    public static CreateUrlRequest createUrlRequestWithExpiration(String longUrl, Instant expiresAt) {
        return new CreateUrlRequest(longUrl, expiresAt);
    }

    // ==================== Test URL Generation ====================

    /**
     * Generates a unique test URL with timestamp.
     *
     * @param suffix URL suffix
     * @return unique test URL
     */
    public static String generateUniqueUrl(String suffix) {
        return "https://www.example.com/" + suffix + "/" + System.nanoTime();
    }

    /**
     * Generates a unique test code with timestamp.
     *
     * @param prefix code prefix
     * @return unique test code
     */
    public static String generateUniqueCode(String prefix) {
        return prefix + System.nanoTime() % 1000000;
    }

    // ==================== Common Test Scenarios ====================

    /**
     * Creates a batch of UrlMappings for bulk testing.
     *
     * @param count number of mappings to create
     * @return array of UrlMappings
     */
    public static UrlMapping[] createUrlMappingBatch(int count) {
        UrlMapping[] mappings = new UrlMapping[count];
        for (int i = 0; i < count; i++) {
            mappings[i] = createUrlMapping("code" + i, "https://example.com/url" + i);
        }
        return mappings;
    }
}
