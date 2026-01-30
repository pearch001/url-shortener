package com.urlshortener.exception;

/**
 * Exception thrown when URL validation fails.
 * This is a runtime exception indicating that the provided URL
 * does not meet the required validation criteria.
 *
 * <p>Common scenarios:
 * <ul>
 *   <li>Malformed URL format</li>
 *   <li>Unsupported protocol</li>
 *   <li>Missing host component</li>
 *   <li>URL contains dangerous patterns</li>
 *   <li>URL exceeds maximum length</li>
 * </ul>
 *
 * <p>This exception is typically caught by the global exception handler
 * and converted to an HTTP 400 Bad Request response.</p>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 * @see com.urlshortener.validation.ValidUrl
 */
public class InvalidUrlException extends RuntimeException {

    /**
     * Constructs a new InvalidUrlException with the specified detail message.
     *
     * @param message the detail message explaining why the URL is invalid
     */
    public InvalidUrlException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidUrlException with the specified detail message
     * and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause of this exception
     */
    public InvalidUrlException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new InvalidUrlException with the specified URL and reason.
     *
     * @param url    the invalid URL
     * @param reason the reason why the URL is invalid
     * @return a new InvalidUrlException instance
     */
    public static InvalidUrlException withReason(String url, String reason) {
        return new InvalidUrlException(
            String.format("Invalid URL '%s': %s", url, reason)
        );
    }
}
