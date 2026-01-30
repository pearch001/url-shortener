package com.urlshortener.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the URL shortener service.
 * Binds properties from application.yml under the prefix "url-shortener".
 *
 * <p>This class centralizes all service-level configuration including:
 * <ul>
 *   <li>Base URL for constructing short URLs</li>
 *   <li>Cleanup job configuration</li>
 *   <li>URL expiration defaults</li>
 *   <li>Rate limiting settings</li>
 * </ul>
 *
 * <p>Example configuration in application.yml:
 * <pre>
 * url-shortener:
 *   base-url: {@code http://localhost:8080}
 *   cleanup:
 *     enabled: true
 *     cron: {@code "0 0 0 * * *"}
 *   url:
 *     expiration-days: 365
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "url-shortener")
@Validated
@Data
public class UrlShortenerProperties {

    /**
     * Base URL for constructing short URLs.
     * Used to generate the full short URL returned to clients.
     *
     * <p>Examples:
     * <ul>
     *   <li>Development: {@code http://localhost:8080}</li>
     *   <li>Production: {@code https://short.example.com}</li>
     * </ul>
     *
     * <p>Must be a valid HTTP/HTTPS URL without trailing slash.
     */
    @NotBlank(message = "Base URL is required")
    @Pattern(regexp = "^https?://[^/]+$", message = "Base URL must be valid HTTP/HTTPS URL without trailing slash")
    private String baseUrl = "http://localhost:8080";

    /**
     * Cleanup job configuration for removing expired URLs.
     */
    private Cleanup cleanup = new Cleanup();

    /**
     * URL-specific configuration.
     */
    private Url url = new Url();

    /**
     * Rate limiting configuration.
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * Cleanup job configuration.
     */
     @Data
     public static class Cleanup {

         /**
          * Whether the cleanup job is enabled.
          * If false, expired URLs will not be automatically removed.
          */
         private boolean enabled = true;

         /**
          * Cron expression for scheduling the cleanup job.
          * Default: {@code "0 0 0 * * *"} (daily at midnight)
          *
          * <p>Examples:
          * <ul>
          *   <li>{@code "0 0 0 * * *"} - Daily at midnight</li>
          *   <li>{@code "0 0 1 * * *"} - Daily at 1 AM</li>
          *   <li>{@code "0 0 0 * * SUN"} - Weekly on Sunday at midnight</li>
          * </ul>
          */
         @NotBlank(message = "Cleanup cron expression is required")
         private String cron = "0 0 0 * * *";

         /**
          * Maximum number of URLs to delete in a single batch.
          * Prevents long-running transactions.
          */
         private int batchSize = 1000;
     }

    /**
     * URL-specific configuration.
     */
    @Data
    public static class Url {

        /**
         * Maximum allowed length for long URLs.
         * Standard browser limit is 2048 characters.
         */
        private int maxLength = 2048;

        /**
         * Default expiration period in days for URLs without explicit expiration.
         * If 0, URLs never expire by default.
         */
        private int expirationDays = 365;

        /**
         * Whether to allow URLs without expiration.
         * If false, all URLs must have an expiration date.
         */
        private boolean allowPermanent = true;
    }

    /**
     * Rate limiting configuration.
     */
    @Data
    public static class RateLimit {

        /**
         * Whether rate limiting is enabled.
         */
        private boolean enabled = true;

        /**
         * Maximum number of requests allowed per minute per client.
         */
        private int requestsPerMinute = 100;

        /**
         * Rate limit key strategy: ip, session, or api-key.
         */
        private String strategy = "ip";
    }

    /**
     * Constructs the full short URL from a code.
     *
     * @param code the short code
     * @return full short URL (e.g., {@code "http://localhost:8080/r/abc123"})
     */
    public String buildShortUrl(String code) {
        return baseUrl + "/r/" + code;
    }

    /**
     * Returns the default expiration in days.
     * Returns 0 if permanent URLs are allowed and no default is set.
     *
     * @return expiration days
     */
    public int getDefaultExpirationDays() {
        return url.expirationDays;
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL cannot be empty");
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("Base URL must start with http:// or https://");
        }

        if (baseUrl.endsWith("/")) {
            throw new IllegalStateException("Base URL must not end with a slash");
        }

        if (cleanup.enabled && (cleanup.cron == null || cleanup.cron.isBlank())) {
            throw new IllegalStateException("Cleanup cron expression is required when cleanup is enabled");
        }

        if (url.expirationDays < 0) {
            throw new IllegalStateException("Expiration days must be non-negative");
        }

        if (rateLimit.enabled && rateLimit.requestsPerMinute <= 0) {
            throw new IllegalStateException("Requests per minute must be positive when rate limiting is enabled");
        }
    }
}
