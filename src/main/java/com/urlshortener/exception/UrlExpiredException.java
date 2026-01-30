package com.urlshortener.exception;

import java.time.Instant;

/**
 * Exception thrown when an attempt is made to access an expired URL mapping.
 * This indicates that the URL mapping exists but has passed its expiration date.
 *
 * <p>Expired URLs are considered invalid and should not be accessible.
 * The system may periodically clean up expired URLs from the database.</p>
 *
 * <p>This exception is caught by the global exception handler and
 * converted to an HTTP 410 Gone response, indicating that the resource
 * was once available but is no longer accessible.</p>
 *
 * <p>Example scenarios:
 * <ul>
 *   <li>Short URL was created with expiration date</li>
 *   <li>Expiration date has passed</li>
 *   <li>Cleanup job hasn't removed the expired URL yet</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
public class UrlExpiredException extends RuntimeException {

    private final String shortCode;
    private final Instant expiredAt;

    /**
     * Constructs a new UrlExpiredException with the specified detail message.
     *
     * @param message the detail message
     */
    public UrlExpiredException(String message) {
        super(message);
        this.shortCode = null;
        this.expiredAt = null;
    }

    /**
     * Constructs a new UrlExpiredException with detailed information.
     *
     * @param message   the detail message
     * @param shortCode the short code of the expired URL
     * @param expiredAt the expiration timestamp
     */
    public UrlExpiredException(String message, String shortCode, Instant expiredAt) {
        super(message);
        this.shortCode = shortCode;
        this.expiredAt = expiredAt;
    }

    /**
     * Returns the short code of the expired URL (if available).
     *
     * @return the short code or null
     */
    public String getShortCode() {
        return shortCode;
    }

    /**
     * Returns the expiration timestamp (if available).
     *
     * @return the expiration timestamp or null
     */
    public Instant getExpiredAt() {
        return expiredAt;
    }

    /**
     * Factory method to create an exception with expiration details.
     *
     * @param shortCode the short code
     * @param expiredAt the expiration timestamp
     * @return a new UrlExpiredException
     */
    public static UrlExpiredException withDetails(String shortCode, Instant expiredAt) {
        String message = String.format(
            "This short URL has expired. Short code: %s, Expired at: %s",
            shortCode, expiredAt
        );
        return new UrlExpiredException(message, shortCode, expiredAt);
    }

    /**
     * Factory method for simple expiration message.
     *
     * @param shortCode the short code
     * @return a new UrlExpiredException
     */
    public static UrlExpiredException forShortCode(String shortCode) {
        return new UrlExpiredException(
            String.format("This short URL has expired: %s", shortCode),
            shortCode,
            null
        );
    }
}
