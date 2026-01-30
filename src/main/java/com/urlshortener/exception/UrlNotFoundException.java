package com.urlshortener.exception;

/**
 * Exception thrown when a requested URL mapping cannot be found.
 * This indicates that no URL mapping exists for the specified short code.
 *
 * <p>Common scenarios:
 * <ul>
 *   <li>Short code does not exist in the database</li>
 *   <li>Short code was typed incorrectly</li>
 *   <li>URL mapping was deleted</li>
 *   <li>Case sensitivity issues (if applicable)</li>
 * </ul>
 *
 * <p>This exception is caught by the global exception handler and
 * converted to an HTTP 404 Not Found response.</p>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
public class UrlNotFoundException extends RuntimeException {

    private final String shortCode;

    /**
     * Constructs a new UrlNotFoundException with the specified detail message.
     *
     * @param message the detail message
     */
    public UrlNotFoundException(String message) {
        super(message);
        this.shortCode = null;
    }

    /**
     * Constructs a new UrlNotFoundException with message and short code.
     *
     * @param message   the detail message
     * @param shortCode the short code that was not found
     */
    public UrlNotFoundException(String message, String shortCode) {
        super(message);
        this.shortCode = shortCode;
    }

    /**
     * Returns the short code that was not found (if available).
     *
     * @return the short code or null
     */
    public String getShortCode() {
        return shortCode;
    }

    /**
     * Factory method to create an exception for a specific short code.
     *
     * @param shortCode the short code that was not found
     * @return a new UrlNotFoundException
     */
    public static UrlNotFoundException forShortCode(String shortCode) {
        return new UrlNotFoundException(
            String.format("URL mapping not found for short code: %s", shortCode),
            shortCode
        );
    }
}
