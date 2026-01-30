package com.urlshortener.exception;

/**
 * Exception thrown when a client exceeds the configured rate limit.
 * This exception indicates that too many requests have been made
 * within a specific time window.
 *
 * <p>Rate limiting helps protect the service from abuse and ensures
 * fair usage across all clients.</p>
 *
 * <p>This exception is caught by the global exception handler and
 * converted to an HTTP 429 Too Many Requests response.</p>
 *
 * <p>Example scenarios:
 * <ul>
 *   <li>Client makes more than allowed requests per minute</li>
 *   <li>Suspicious patterns of rapid URL creation</li>
 *   <li>DDoS protection triggered</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
public class RateLimitExceededException extends RuntimeException {

    private final String clientIdentifier;
    private final long retryAfterSeconds;

    /**
     * Constructs a new RateLimitExceededException with a default message.
     *
     * @param message the detail message
     */
    public RateLimitExceededException(String message) {
        super(message);
        this.clientIdentifier = null;
        this.retryAfterSeconds = 60; // Default to 60 seconds
    }

    /**
     * Constructs a new RateLimitExceededException with detailed information.
     *
     * @param message            the detail message
     * @param clientIdentifier   identifier of the client (IP address, API key, etc.)
     * @param retryAfterSeconds  number of seconds to wait before retrying
     */
    public RateLimitExceededException(
            String message,
            String clientIdentifier,
            long retryAfterSeconds) {
        super(message);
        this.clientIdentifier = clientIdentifier;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Returns the client identifier (if available).
     *
     * @return client identifier or null
     */
    public String getClientIdentifier() {
        return clientIdentifier;
    }

    /**
     * Returns the number of seconds the client should wait before retrying.
     *
     * @return retry-after time in seconds
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * Factory method to create an exception for a specific client.
     *
     * @param clientIdentifier  the client identifier
     * @param limit             the rate limit threshold
     * @param windowSeconds     the time window in seconds
     * @param retryAfterSeconds seconds to wait before retrying
     * @return a new RateLimitExceededException
     */
    public static RateLimitExceededException forClient(
            String clientIdentifier,
            int limit,
            int windowSeconds,
            long retryAfterSeconds) {
        String message = String.format(
            "Rate limit exceeded for client '%s'. " +
            "Limit: %d requests per %d seconds. Please retry after %d seconds.",
            clientIdentifier, limit, windowSeconds, retryAfterSeconds
        );
        return new RateLimitExceededException(message, clientIdentifier, retryAfterSeconds);
    }

    /**
     * Factory method with default retry-after time.
     *
     * @param limit         the rate limit threshold
     * @param windowSeconds the time window in seconds
     * @return a new RateLimitExceededException
     */
    public static RateLimitExceededException withDefaults(int limit, int windowSeconds) {
        String message = String.format(
            "Rate limit exceeded. Limit: %d requests per %d seconds. " +
            "Please retry after 60 seconds.",
            limit, windowSeconds
        );
        return new RateLimitExceededException(message, null, 60);
    }
}
