package com.urlshortener.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the URL code generator.
 * Binds properties from application.yml under the prefix "url-shortener.code".
 *
 * <p>Configurable parameters:
 * <ul>
 *   <li>length: Length of generated short codes (default: 7)</li>
 *   <li>maxRetries: Maximum retry attempts on collision (default: 5)</li>
 *   <li>charset: Characters allowed in codes (default: Base62)</li>
 *   <li>strategy: Generation strategy (random or hash-based)</li>
 *   <li>hashAlgorithm: Algorithm for hash-based generation</li>
 * </ul>
 *
 * <p>Example configuration in application.yml:
 * <pre>
 * url-shortener:
 *   code:
 *     length: 7
 *     max-retries: 5
 *     charset: "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
 *     strategy: random
 *     hash-algorithm: SHA-256
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "url-shortener.code")
@Validated
@Data
public class CodeGeneratorProperties {

    /**
     * Length of generated short codes.
     * Longer codes provide more combinations but are less user-friendly.
     *
     * <p>Combinations by length:
     * <ul>
     *   <li>6 chars: 62^6 = ~56 billion</li>
     *   <li>7 chars: 62^7 = ~3.5 trillion</li>
     *   <li>8 chars: 62^8 = ~218 trillion</li>
     * </ul>
     *
     * <p>Default: 7 characters</p>
     */
    @Min(value = 4, message = "Code length must be at least 4")
    @Max(value = 12, message = "Code length must not exceed 12")
    private int length = 7;

    /**
     * Maximum number of retry attempts when a collision is detected.
     * After this many retries, code generation fails.
     *
     * <p>Default: 5 retries</p>
     */
    @Min(value = 1, message = "Max retries must be at least 1")
    @Max(value = 10, message = "Max retries must not exceed 10")
    private int maxRetries = 5;

    /**
     * Character set used for code generation.
     * Must contain only URL-safe characters.
     *
     * <p>Default: Base62 (0-9, a-z, A-Z)</p>
     */
    @NotBlank(message = "Charset cannot be blank")
    private String charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Code generation strategy.
     * Options:
     * <ul>
     *   <li>random: Cryptographically random codes (default)</li>
     *   <li>hash: Deterministic hash-based codes</li>
     *   <li>counter: Sequential counter-based codes (not recommended)</li>
     * </ul>
     *
     * <p>Default: random</p>
     */
    @NotBlank(message = "Strategy cannot be blank")
    private String strategy = "random";

    /**
     * Hash algorithm for hash-based code generation.
     * Used only when strategy is "hash".
     *
     * <p>Supported algorithms: MD5, SHA-1, SHA-256, SHA-512</p>
     * <p>Default: SHA-256 (recommended for security)</p>
     */
    @NotBlank(message = "Hash algorithm cannot be blank")
    private String hashAlgorithm = "SHA-256";

    /**
     * Whether to use salt in hash-based generation.
     * Adds randomness to prevent predictable codes.
     *
     * <p>Default: true</p>
     */
    private boolean useSalt = true;

    /**
     * Timeout in milliseconds for code generation.
     * Prevents infinite loops in case of issues.
     *
     * <p>Default: 1000ms (1 second)</p>
     */
    @Min(value = 100, message = "Timeout must be at least 100ms")
    @Max(value = 5000, message = "Timeout must not exceed 5000ms")
    private long timeoutMs = 1000;

    /**
     * Whether to enable collision logging.
     * Useful for monitoring and debugging.
     *
     * <p>Default: true</p>
     */
    private boolean logCollisions = true;

    /**
     * Returns the charset size (number of unique characters).
     *
     * @return charset size
     */
    public int getCharsetSize() {
        return charset.length();
    }

    /**
     * Calculates the total number of possible combinations.
     *
     * @return total combinations (charset^length)
     */
    public long getTotalCombinations() {
        return (long) Math.pow(getCharsetSize(), length);
    }

    /**
     * Validates the current configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (charset == null || charset.isEmpty()) {
            throw new IllegalStateException("Charset cannot be empty");
        }

        if (length < 4 || length > 12) {
            throw new IllegalStateException("Code length must be between 4 and 12");
        }

        if (maxRetries < 1 || maxRetries > 10) {
            throw new IllegalStateException("Max retries must be between 1 and 10");
        }

        // Check for duplicate characters in charset
        if (charset.chars().distinct().count() != charset.length()) {
            throw new IllegalStateException("Charset contains duplicate characters");
        }

        // Validate strategy
        if (!strategy.matches("random|hash|counter")) {
            throw new IllegalStateException("Invalid strategy: " + strategy);
        }
    }

    /**
     * Returns a human-readable description of the configuration.
     *
     * @return configuration description
     */
    @Override
    public String toString() {
        return String.format(
            "CodeGeneratorProperties{length=%d, maxRetries=%d, strategy='%s', " +
            "charsetSize=%d, totalCombinations=%,d}",
            length, maxRetries, strategy, getCharsetSize(), getTotalCombinations()
        );
    }
}
