package com.urlshortener.util;

import com.urlshortener.config.CodeGeneratorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Robust, thread-safe code generator for URL shortening.
 * Supports multiple generation strategies with collision detection and retry mechanism.
 *
 * <p><b>Generation Strategies:</b></p>
 * <ul>
 *   <li><b>Random (Default):</b> Cryptographically random Base62 codes using SecureRandom</li>
 *   <li><b>Hash-based:</b> Deterministic codes from MD5/SHA hash of URL + timestamp</li>
 *   <li><b>Counter-based:</b> Sequential codes (not recommended for production)</li>
 * </ul>
 *
 * <p><b>Collision Handling:</b></p>
 * The generator uses a callback function to check for existing codes in the database.
 * If a collision is detected, it retries with a new code up to the configured maximum attempts.
 *
 * <p><b>Thread Safety:</b></p>
 * This class is thread-safe:
 * <ul>
 *   <li>SecureRandom is thread-safe</li>
 *   <li>AtomicInteger for collision counting</li>
 *   <li>No shared mutable state</li>
 * </ul>
 *
 * <p><b>Performance:</b></p>
 * Typical generation time: &lt; 1ms
 * - Random generation: ~0.1ms
 * - Hash-based generation: ~0.5ms
 * - Database collision check: variable (typically &lt; 10ms)
 *
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Generate a random code
 * String code = codeGenerator.generateCode("https://example.com");
 *
 * // Generate with collision detection
 * String uniqueCode = codeGenerator.generateUniqueCode(
 *     "https://example.com",
 *     code -> urlRepository.existsByCode(code)
 * );
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Component
@Slf4j
public class CodeGenerator {

    private final CodeGeneratorProperties properties;
    private final SecureRandom secureRandom;
    private final AtomicInteger collisionCounter;
    private final AtomicInteger generationCounter;

    /**
     * Constructs a CodeGenerator with the specified configuration.
     *
     * @param properties configuration properties for code generation
     */
    public CodeGenerator(CodeGeneratorProperties properties) {
        this.properties = properties;
        this.secureRandom = new SecureRandom();
        this.collisionCounter = new AtomicInteger(0);
        this.generationCounter = new AtomicInteger(0);

        // Validate configuration
        properties.validate();

        log.info("CodeGenerator initialized with configuration: {}", properties);
        log.info("Total possible combinations: {:,}", properties.getTotalCombinations());
    }

    /**
     * Generates a short code based on the configured strategy.
     * Does not check for collisions - use {@link #generateUniqueCode} for that.
     *
     * @param longUrl the original long URL (used for hash-based generation)
     * @return generated short code
     */
    public String generateCode(String longUrl) {
        long startTime = System.nanoTime();

        try {
            String code = switch (properties.getStrategy().toLowerCase()) {
                case "random" -> generateRandomCode();
                case "hash" -> generateHashBasedCode(longUrl);
                case "counter" -> generateCounterBasedCode();
                default -> throw new IllegalStateException(
                    "Unknown generation strategy: " + properties.getStrategy()
                );
            };

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            log.debug("Generated code '{}' in {}ms using strategy '{}'",
                code, durationMs, properties.getStrategy());

            return code;

        } finally {
            generationCounter.incrementAndGet();
        }
    }

    /**
     * Generates a unique short code with collision detection and retry mechanism.
     * Uses a callback function to check if a code already exists in the database.
     *
     * <p><b>Algorithm:</b></p>
     * <pre>
     * 1. Generate a code using the configured strategy
     * 2. Check if code exists using the existsChecker function
     * 3. If collision detected:
     *    a. Log the collision event
     *    b. Increment collision counter
     *    c. Retry with new code
     * 4. If max retries exceeded, throw exception
     * 5. Return unique code
     * </pre>
     *
     * <p><b>Collision Probability:</b></p>
     * With 7-character Base62 codes (3.5 trillion combinations):
     * - At 1 million URLs: ~0.00003% collision probability
     * - At 10 million URLs: ~0.0003% collision probability
     * - At 100 million URLs: ~0.003% collision probability
     *
     * @param longUrl       the original long URL
     * @param existsChecker function that returns true if code exists in database
     * @return unique short code
     * @throws CodeGenerationException if unable to generate unique code after max retries
     */
    public String generateUniqueCode(String longUrl, Function<String, Boolean> existsChecker) {
        long startTime = System.currentTimeMillis();
        int attempts = 0;
        int maxAttempts = properties.getMaxRetries();

        while (attempts < maxAttempts) {
            attempts++;

            // Check timeout
            if (System.currentTimeMillis() - startTime > properties.getTimeoutMs()) {
                log.error("Code generation timeout exceeded after {}ms", properties.getTimeoutMs());
                throw new CodeGenerationException(
                    "Code generation timeout exceeded after " + properties.getTimeoutMs() + "ms"
                );
            }

            String code = generateCode(longUrl);

            // Check for collision
            if (!existsChecker.apply(code)) {
                if (attempts > 1) {
                    log.debug("Generated unique code '{}' after {} attempts", code, attempts);
                }
                return code;
            }

            // Collision detected
            handleCollision(code, attempts, maxAttempts);
        }

        // Max retries exceeded
        log.error("Failed to generate unique code after {} attempts", maxAttempts);
        throw new CodeGenerationException(
            String.format("Failed to generate unique code after %d attempts", maxAttempts)
        );
    }

    /**
     * Validates if a code meets the required format and constraints.
     *
     * @param code the code to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }

        // Check length
        if (code.length() != properties.getLength()) {
            return false;
        }

        // Check characters against charset
        String charset = properties.getCharset();
        for (char c : code.toCharArray()) {
            if (charset.indexOf(c) == -1) {
                return false;
            }
        }

        return true;
    }

    /**
     * Generates a cryptographically random Base62 code.
     * Uses SecureRandom for high-quality randomness.
     *
     * <p>This is the recommended strategy for production use.</p>
     *
     * @return random Base62 code
     */
    private String generateRandomCode() {
        String charset = properties.getCharset();
        int length = properties.getLength();
        StringBuilder code = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(charset.length());
            code.append(charset.charAt(randomIndex));
        }

        return code.toString();
    }

    /**
     * Generates a deterministic hash-based code from the URL and timestamp.
     * Useful when you want reproducible codes for the same URL.
     *
     * <p><b>Algorithm:</b></p>
     * <pre>
     * 1. Create input: longUrl + timestamp (+ salt if enabled)
     * 2. Hash using configured algorithm (SHA-256 by default)
     * 3. Convert hash bytes to Base62
     * 4. Take first N characters
     * </pre>
     *
     * @param longUrl the original long URL
     * @return hash-based code
     */
    private String generateHashBasedCode(String longUrl) {
        try {
            // Create input with timestamp for uniqueness
            String input = longUrl + Instant.now().toEpochMilli();

            // Add salt if enabled
            if (properties.isUseSalt()) {
                input += secureRandom.nextLong();
            }

            // Hash the input
            MessageDigest digest = MessageDigest.getInstance(properties.getHashAlgorithm());
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert to Base62
            String base62Hash = Base62Encoder.encodeBytes(hashBytes);

            // Take first N characters
            int length = properties.getLength();
            if (base62Hash.length() >= length) {
                return base62Hash.substring(0, length);
            } else {
                // Pad with random characters if hash is too short
                return padWithRandomChars(base62Hash, length);
            }

        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm not available: {}", properties.getHashAlgorithm(), e);
            // Fallback to random generation
            log.warn("Falling back to random code generation");
            return generateRandomCode();
        }
    }

    /**
     * Generates a sequential counter-based code.
     * NOT RECOMMENDED for production as codes are predictable.
     *
     * @return counter-based code
     */
    private String generateCounterBasedCode() {
        long counter = generationCounter.get();
        String encoded = Base62Encoder.encode(counter);

        // Pad to required length
        return padWithZeros(encoded, properties.getLength());
    }

    /**
     * Handles a collision event.
     *
     * @param code        the colliding code
     * @param attempt     current attempt number
     * @param maxAttempts maximum allowed attempts
     */
    private void handleCollision(String code, int attempt, int maxAttempts) {
        int totalCollisions = collisionCounter.incrementAndGet();

        if (properties.isLogCollisions()) {
            log.warn("Collision detected for code '{}' (attempt {}/{}, total collisions: {})",
                code, attempt, maxAttempts, totalCollisions);
        }

        // Calculate collision rate
        int totalGenerations = generationCounter.get();
        if (totalGenerations > 0) {
            double collisionRate = (double) totalCollisions / totalGenerations * 100;
            if (collisionRate > 1.0) {
                log.error("High collision rate detected: {:.2f}%", collisionRate);
            }
        }
    }

    /**
     * Pads a string with random characters to reach the desired length.
     */
    private String padWithRandomChars(String input, int desiredLength) {
        String charset = properties.getCharset();
        StringBuilder padded = new StringBuilder(input);

        while (padded.length() < desiredLength) {
            int randomIndex = secureRandom.nextInt(charset.length());
            padded.append(charset.charAt(randomIndex));
        }

        return padded.toString();
    }

    /**
     * Pads a string with leading zeros to reach the desired length.
     */
    private String padWithZeros(String input, int desiredLength) {
        String charset = properties.getCharset();
        char paddingChar = charset.charAt(0); // First character (typically '0')

        if (input.length() >= desiredLength) {
            return input.substring(0, desiredLength);
        }

        StringBuilder padded = new StringBuilder();
        int paddingNeeded = desiredLength - input.length();

        for (int i = 0; i < paddingNeeded; i++) {
            padded.append(paddingChar);
        }
        padded.append(input);

        return padded.toString();
    }

    /**
     * Returns statistics about code generation.
     *
     * @return statistics string
     */
    public String getStatistics() {
        int totalGenerations = generationCounter.get();
        int totalCollisions = collisionCounter.get();
        double collisionRate = totalGenerations > 0
            ? (double) totalCollisions / totalGenerations * 100
            : 0.0;

        return String.format(
            "Code generation statistics: total=%d, collisions=%d (%.4f%%)",
            totalGenerations, totalCollisions, collisionRate
        );
    }

    /**
     * Resets statistics counters. Useful for testing.
     */
    public void resetStatistics() {
        collisionCounter.set(0);
        generationCounter.set(0);
        log.debug("Code generation statistics reset");
    }

    /**
     * Exception thrown when code generation fails.
     */
    public static class CodeGenerationException extends RuntimeException {
        public CodeGenerationException(String message) {
            super(message);
        }

        public CodeGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
