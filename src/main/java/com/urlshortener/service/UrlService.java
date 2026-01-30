package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlMetadataResponse;
import com.urlshortener.dto.UrlResponse;

/**
 * Service interface for URL shortening operations.
 * Defines the contract for core business logic of the URL shortener.
 *
 * <p>This interface provides methods for:
 * <ul>
 *   <li>Creating shortened URLs with collision handling</li>
 *   <li>Resolving short codes to original URLs</li>
 *   <li>Retrieving URL metadata and statistics</li>
 *   <li>Cleaning up expired URLs</li>
 * </ul>
 *
 * <p><b>Implementation Notes:</b></p>
 * <ul>
 *   <li>All database operations should be transactional</li>
 *   <li>Thread-safe for concurrent access</li>
 *   <li>Idempotent behavior for duplicate URL submissions</li>
 *   <li>Atomic hitCount increments</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 * @see UrlServiceImpl
 */
public interface UrlService {

    /**
     * Creates a shortened URL from a long URL.
     * This operation is idempotent - submitting the same URL multiple times
     * returns the same short code.
     *
     * <p><b>Business Logic:</b></p>
     * <ol>
     *   <li>Validate the request (handled by @Valid annotation)</li>
     *   <li>Check if URL already exists in database</li>
     *   <li>If exists and not expired: return existing mapping</li>
     *   <li>If new: generate unique code using CodeGenerator</li>
     *   <li>Handle collisions with retry mechanism</li>
     *   <li>Save to database</li>
     *   <li>Return UrlResponse with constructed short URL</li>
     * </ol>
     *
     * <p><b>Idempotency Behavior:</b></p>
     * When the same longUrl is submitted multiple times, the service returns
     * the existing short code rather than generating a new one. This ensures:
     * <ul>
     *   <li>Consistent short codes for the same URL</li>
     *   <li>Prevents database bloat</li>
     *   <li>Predictable behavior for clients</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b></p>
     * This method is thread-safe. If multiple threads submit the same URL
     * simultaneously, they may receive different codes due to race conditions.
     * This is acceptable as both codes point to the same destination.
     *
     * @param request the URL creation request containing longUrl and optional expiresAt
     * @return UrlResponse containing the short code and full short URL
     * @throws com.urlshortener.exception.InvalidUrlException if URL validation fails
     * @throws com.urlshortener.util.CodeGenerator.CodeGenerationException if unable to generate unique code
     */
    UrlResponse createShortUrl(CreateUrlRequest request);

    /**
     * Resolves a short code to its original long URL.
     * This method is called during redirect operations.
     *
     * <p><b>Business Logic:</b></p>
     * <ol>
     *   <li>Find URL mapping by short code</li>
     *   <li>Check if mapping exists</li>
     *   <li>Check if URL has expired</li>
     *   <li>Increment hit count atomically (separate transaction)</li>
     *   <li>Return the long URL for redirection</li>
     * </ol>
     *
     * <p><b>Hit Count Tracking:</b></p>
     * The hit count is incremented atomically using a database UPDATE query.
     * This prevents race conditions when multiple requests access the same
     * short URL concurrently.
     *
     * <p><b>Performance Consideration:</b></p>
     * This is a high-frequency operation (hot path). The implementation:
     * <ul>
     *   <li>Uses database index on code column for fast lookup</li>
     *   <li>Performs hitCount increment asynchronously if possible</li>
     *   <li>Does not fetch full entity if only URL is needed</li>
     * </ul>
     *
     * @param code the short code to resolve
     * @return the original long URL
     * @throws com.urlshortener.exception.UrlNotFoundException if code doesn't exist
     * @throws com.urlshortener.exception.UrlExpiredException if URL has expired
     */
    String resolveShortUrl(String code);

    /**
     * Retrieves comprehensive metadata for a shortened URL.
     * Includes usage statistics and all associated data.
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Analytics and reporting</li>
     *   <li>URL management dashboards</li>
     *   <li>Monitoring URL performance</li>
     *   <li>Debugging and troubleshooting</li>
     * </ul>
     *
     * <p><b>Returned Information:</b></p>
     * <ul>
     *   <li>Short code and full short URL</li>
     *   <li>Original long URL</li>
     *   <li>Creation and expiration timestamps</li>
     *   <li>Hit count (number of accesses)</li>
     * </ul>
     *
     * @param code the short code to query
     * @return UrlMetadataResponse containing all metadata
     * @throws com.urlshortener.exception.UrlNotFoundException if code doesn't exist
     */
    UrlMetadataResponse getUrlMetadata(String code);

    /**
     * Removes expired URL mappings from the database.
     * This method should be called periodically by a scheduled task.
     *
     * <p><b>Cleanup Strategy:</b></p>
     * <ul>
     *   <li>Finds all URLs where expiresAt &lt; current time</li>
     *   <li>Deletes them in a single batch operation</li>
     *   <li>Logs the number of URLs deleted</li>
     *   <li>Can be called manually or via scheduled task</li>
     * </ul>
     *
     * <p><b>Performance:</b></p>
     * Uses bulk delete query with index on expiresAt column for efficiency.
     * The operation is transactional to ensure data consistency.
     *
     * <p><b>Scheduling:</b></p>
     * Typically scheduled to run daily at midnight using @Scheduled annotation.
     * Configurable via application.yml:
     * <pre>
     * url-shortener:
     *   cleanup:
     *     enabled: true
     *     cron: "0 0 0 * * *"  # Daily at midnight
     * </pre>
     *
     * @return the number of expired URLs deleted
     */
    int cleanupExpiredUrls();

    /**
     * Deletes a URL mapping by its short code.
     * Allows manual removal of URLs before they expire.
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Content moderation (remove inappropriate URLs)</li>
     *   <li>User-requested deletion</li>
     *   <li>Administrative cleanup</li>
     * </ul>
     *
     * @param code the short code to delete
     * @throws com.urlshortener.exception.UrlNotFoundException if code doesn't exist
     */
    void deleteShortUrl(String code);

    /**
     * Updates the expiration time for an existing URL mapping.
     * Allows extending or modifying the lifetime of a short URL.
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Extend expiration for important URLs</li>
     *   <li>Make temporary URLs permanent (set expiresAt to null)</li>
     *   <li>Shorten expiration for time-sensitive content</li>
     * </ul>
     *
     * @param code      the short code to update
     * @param expiresAt the new expiration timestamp (null for no expiration)
     * @throws com.urlshortener.exception.UrlNotFoundException if code doesn't exist
     */
    void updateExpiration(String code, java.time.Instant expiresAt);
}
