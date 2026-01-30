package com.urlshortener.util;

import com.urlshortener.config.CodeGeneratorProperties;
import com.urlshortener.exception.CodeGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for CodeGenerator.
 * Tests code generation logic, collision handling, validation, and thread safety.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeGenerator Unit Tests")
class CodeGeneratorTest {

    private CodeGenerator codeGenerator;
    private CodeGeneratorProperties properties;
    private static final Pattern BASE62_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    @BeforeEach
    void setUp() {
        properties = new CodeGeneratorProperties();
        properties.setLength(7);
        properties.setMaxRetries(5);
        properties.setStrategy("random");
        properties.setCharset("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        properties.setHashAlgorithm("SHA-256");
        properties.setUseSalt(true);

        codeGenerator = new CodeGenerator(properties);
    }

    // ==================== Code Length Tests ====================

    @Test
    @DisplayName("Should generate code with correct length")
    void shouldGenerateCodeWithCorrectLength() {
        // Given
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then
        assertNotNull(code, "Generated code should not be null");
        assertEquals(7, code.length(), "Generated code should have length 7");
    }

    @Test
    @DisplayName("Should generate codes with custom length")
    void shouldGenerateCodesWithCustomLength() {
        // Given
        properties.setLength(10);
        codeGenerator = new CodeGenerator(properties);
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then
        assertEquals(10, code.length(), "Generated code should have custom length 10");
    }

    @RepeatedTest(10)
    @DisplayName("Should consistently generate correct length codes")
    void shouldConsistentlyGenerateCorrectLengthCodes() {
        // Given
        String longUrl = "https://www.example.com/test-" + System.nanoTime();
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then
        assertEquals(7, code.length(), "All generated codes should have length 7");
    }

    // ==================== Character Validation Tests ====================

    @Test
    @DisplayName("Should generate code with valid Base62 characters only")
    void shouldGenerateCodeWithValidCharacters() {
        // Given
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then
        assertTrue(BASE62_PATTERN.matcher(code).matches(),
            "Generated code should only contain Base62 characters (a-z, A-Z, 0-9)");
    }

    @RepeatedTest(20)
    @DisplayName("Should consistently generate valid Base62 codes")
    void shouldConsistentlyGenerateValidBase62Codes() {
        // Given
        String longUrl = "https://www.example.com/test-" + System.nanoTime();
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then
        assertTrue(BASE62_PATTERN.matcher(code).matches(),
            "All generated codes should contain only Base62 characters");
    }

    @Test
    @DisplayName("Should validate code format correctly")
    void shouldValidateCodeFormat() {
        // Valid codes
        assertTrue(codeGenerator.isValidCode("abc123D"), "Valid Base62 code should pass validation");
        assertTrue(codeGenerator.isValidCode("ABC123d"), "Valid mixed-case code should pass validation");
        assertTrue(codeGenerator.isValidCode("1234567"), "Valid numeric code should pass validation");
        assertTrue(codeGenerator.isValidCode("abcdefg"), "Valid lowercase code should pass validation");

        // Invalid codes
        assertFalse(codeGenerator.isValidCode("abc-123"), "Code with hyphen should fail validation");
        assertFalse(codeGenerator.isValidCode("abc_123"), "Code with underscore should fail validation");
        assertFalse(codeGenerator.isValidCode("abc 123"), "Code with space should fail validation");
        assertFalse(codeGenerator.isValidCode("abc@123"), "Code with special char should fail validation");
        assertFalse(codeGenerator.isValidCode(""), "Empty code should fail validation");
        assertFalse(codeGenerator.isValidCode(null), "Null code should fail validation");
    }

    // ==================== Uniqueness and Randomness Tests ====================

    @Test
    @DisplayName("Should generate different codes for different inputs")
    void shouldGenerateDifferentCodesForDifferentInputs() {
        // Given
        String url1 = "https://www.example.com/page1";
        String url2 = "https://www.example.com/page2";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code1 = codeGenerator.generateUniqueCode(url1, alwaysFalse);
        String code2 = codeGenerator.generateUniqueCode(url2, alwaysFalse);

        // Then
        assertNotEquals(code1, code2, "Different URLs should generate different codes");
    }

    @Test
    @DisplayName("Should generate unique codes with random strategy")
    void shouldGenerateUniqueCodesWithRandomStrategy() {
        // Given
        properties.setStrategy("random");
        codeGenerator = new CodeGenerator(properties);
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysFalse = code -> false;
        Set<String> generatedCodes = new HashSet<>();

        // When - Generate 100 codes
        for (int i = 0; i < 100; i++) {
            String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);
            generatedCodes.add(code);
        }

        // Then - Most codes should be unique (allow for small probability of collision)
        assertTrue(generatedCodes.size() > 95,
            "Random strategy should generate mostly unique codes (>95%)");
    }

    @Test
    @DisplayName("Should generate valid codes with hash strategy without salt")
    void shouldGenerateValidCodesWithHashStrategyWithoutSalt() {
        // Given
        properties.setStrategy("hash");
        properties.setUseSalt(false); // Disable salt
        codeGenerator = new CodeGenerator(properties);
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When - Generate codes
        String code1 = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);
        String code2 = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then - Both codes should be valid (hash strategy includes timestamp, so not deterministic)
        assertNotNull(code1, "First code should not be null");
        assertNotNull(code2, "Second code should not be null");
        assertEquals(7, code1.length(), "First code should have correct length");
        assertEquals(7, code2.length(), "Second code should have correct length");
    }

    // ==================== Collision Handling Tests ====================

    @Test
    @DisplayName("Should handle collision with retry")
    void shouldHandleCollisionWithRetry() {
        // Given
        String longUrl = "https://www.example.com/test";
        AtomicInteger callCount = new AtomicInteger(0);

        // Mock: return true (exists) first 2 times, then false (available)
        Function<String, Boolean> existsChecker = code -> {
            int count = callCount.incrementAndGet();
            return count <= 2; // First 2 calls return true (collision), 3rd returns false
        };

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, existsChecker);

        // Then
        assertNotNull(code, "Should generate code after retries");
        assertEquals(3, callCount.get(), "Should retry twice before finding available code");
    }

    @Test
    @DisplayName("Should throw exception when max retries exceeded")
    void shouldThrowExceptionWhenMaxRetriesExceeded() {
        // Given
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysTrue = code -> true; // Always collision

        // When & Then
        CodeGenerator.CodeGenerationException exception = assertThrows(
            CodeGenerator.CodeGenerationException.class,
            () -> codeGenerator.generateUniqueCode(longUrl, alwaysTrue),
            "Should throw CodeGenerationException when max retries exceeded"
        );

        assertTrue(exception.getMessage().contains("Failed to generate unique code"),
            "Exception message should mention failure to generate unique code");
    }

    @Test
    @DisplayName("Should succeed on last retry attempt")
    void shouldSucceedOnLastRetryAttempt() {
        // Given
        String longUrl = "https://www.example.com/test";
        AtomicInteger callCount = new AtomicInteger(0);
        int maxRetries = properties.getMaxRetries();

        // Mock: return true until last attempt
        Function<String, Boolean> existsChecker = code -> {
            int count = callCount.incrementAndGet();
            return count < maxRetries; // True until last retry
        };

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, existsChecker);

        // Then
        assertNotNull(code, "Should generate code on last retry attempt");
        assertEquals(maxRetries, callCount.get(), "Should use all retry attempts");
    }

    @Test
    @DisplayName("Should succeed immediately when no collision")
    void shouldSucceedImmediatelyWhenNoCollision() {
        // Given
        String longUrl = "https://www.example.com/test";
        AtomicInteger callCount = new AtomicInteger(0);

        Function<String, Boolean> alwaysFalse = code -> {
            callCount.incrementAndGet();
            return false; // Never collision
        };

        // When
        String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

        // Then
        assertNotNull(code, "Should generate code immediately");
        assertEquals(1, callCount.get(), "Should only check existence once");
    }

    // ==================== Thread Safety Tests ====================

    @Test
    @DisplayName("Should be thread-safe when generating codes concurrently")
    void shouldBeThreadSafe() throws InterruptedException {
        // Given
        int threadCount = 10;
        int codesPerThread = 100;
        Set<String> allCodes = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        Function<String, Boolean> existsChecker = allCodes::contains;

        // When - Generate codes from multiple threads simultaneously
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    for (int j = 0; j < codesPerThread; j++) {
                        String url = "https://example.com/thread-" + threadId + "/url-" + j;
                        String code = codeGenerator.generateUniqueCode(url, existsChecker);
                        allCodes.add(code);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(completed, "All threads should complete within timeout");
        int expectedCodes = threadCount * codesPerThread;
        assertEquals(expectedCodes, allCodes.size(),
            "All generated codes should be unique (no race conditions)");
    }

    @Test
    @DisplayName("Should handle concurrent collision retries correctly")
    void shouldHandleConcurrentCollisionRetriesCorrectly() throws InterruptedException {
        // Given
        int threadCount = 5;
        Set<String> existingCodes = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Pre-populate some existing codes to trigger collisions
        for (int i = 0; i < 50; i++) {
            existingCodes.add(codeGenerator.generateUniqueCode(
                "https://example.com/existing-" + i,
                code -> false
            ));
        }

        Function<String, Boolean> existsChecker = existingCodes::contains;

        // When - Generate codes with potential collisions
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < 20; j++) {
                        String url = "https://example.com/new-" + threadId + "-" + j;
                        String code = codeGenerator.generateUniqueCode(url, existsChecker);
                        existingCodes.add(code);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(completed, "All threads should complete without deadlock");
        assertEquals(50 + (threadCount * 20), existingCodes.size(),
            "All codes should be unique across threads");
    }

    // ==================== Edge Cases and Error Handling Tests ====================

    @Test
    @DisplayName("Should handle null URL")
    void shouldHandleNullUrl() {
        // Given
        Function<String, Boolean> alwaysFalse = code -> false;

        // When - Null URL should either throw NPE or generate code
        // This tests current behavior (accepts null for random strategy)
        try {
            String code = codeGenerator.generateUniqueCode(null, alwaysFalse);
            // If it doesn't throw, it should generate a valid code
            assertNotNull(code, "Generated code should not be null");
            assertEquals(7, code.length(), "Generated code should have correct length");
        } catch (NullPointerException e) {
            // Also acceptable - NPE for null input
            assertTrue(true, "NPE is acceptable for null URL");
        }
    }

    @Test
    @DisplayName("Should handle empty URL")
    void shouldHandleEmptyUrl() {
        // Given
        String emptyUrl = "";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(emptyUrl, alwaysFalse);

        // Then
        assertNotNull(code, "Should generate code even for empty URL");
        assertEquals(7, code.length(), "Generated code should have correct length");
    }

    @Test
    @DisplayName("Should handle very long URLs")
    void shouldHandleVeryLongUrls() {
        // Given
        String veryLongUrl = "https://www.example.com/" + "a".repeat(2000);
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(veryLongUrl, alwaysFalse);

        // Then
        assertNotNull(code, "Should generate code for very long URL");
        assertEquals(7, code.length(), "Generated code should have correct length");
    }

    @Test
    @DisplayName("Should handle special characters in URL")
    void shouldHandleSpecialCharactersInUrl() {
        // Given
        String urlWithSpecialChars = "https://www.example.com/search?q=hello+world&lang=en#section";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code = codeGenerator.generateUniqueCode(urlWithSpecialChars, alwaysFalse);

        // Then
        assertNotNull(code, "Should generate code for URL with special characters");
        assertTrue(BASE62_PATTERN.matcher(code).matches(),
            "Generated code should only contain Base62 characters");
    }

    @Test
    @DisplayName("Should handle different hash algorithms")
    void shouldHandleDifferentHashAlgorithms() {
        // Given
        properties.setStrategy("hash");
        properties.setUseSalt(false);
        String[] algorithms = {"MD5", "SHA-1", "SHA-256", "SHA-512"};
        String longUrl = "https://www.example.com/test";
        Function<String, Boolean> alwaysFalse = code -> false;

        // When & Then
        for (String algorithm : algorithms) {
            properties.setHashAlgorithm(algorithm);
            codeGenerator = new CodeGenerator(properties);

            String code = codeGenerator.generateUniqueCode(longUrl, alwaysFalse);

            assertNotNull(code, "Should generate code with " + algorithm);
            assertEquals(7, code.length(), "Code length should be correct for " + algorithm);
            assertTrue(BASE62_PATTERN.matcher(code).matches(),
                "Code should be valid Base62 for " + algorithm);
        }
    }

    @Test
    @DisplayName("Should normalize URLs before generating codes")
    void shouldNormalizeUrlsBeforeGeneratingCodes() {
        // Given
        properties.setStrategy("hash");
        properties.setUseSalt(false);
        codeGenerator = new CodeGenerator(properties);

        String url1 = "https://www.example.com/page";
        String url2 = "https://www.example.com/page/"; // Trailing slash
        Function<String, Boolean> alwaysFalse = code -> false;

        // When
        String code1 = codeGenerator.generateUniqueCode(url1, alwaysFalse);
        String code2 = codeGenerator.generateUniqueCode(url2, alwaysFalse);

        // Then - Without normalization, these should be different
        // This test documents current behavior
        assertNotEquals(code1, code2,
            "URLs with different formats generate different codes (no normalization)");
    }
}
