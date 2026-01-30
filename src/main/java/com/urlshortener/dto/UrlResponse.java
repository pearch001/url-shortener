package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO returned after successfully creating a shortened URL.
 * Uses Java record for immutability and concise syntax.
 *
 * <p>This response includes all essential information about the newly
 * created short URL, including both the short code and the full short URL.</p>
 *
 * <p>Example JSON response:
 * <pre>
 * {
 *   "code": "abc123d",
 *   "shortUrl": "http://localhost:8080/r/abc123d",
 *   "longUrl": "https://www.example.com/very/long/path",
 *   "createdAt": "2026-01-27T10:30:00Z",
 *   "expiresAt": "2027-01-27T10:30:00Z"
 * }
 * </pre>
 *
 * @param code       The unique short code identifying this URL mapping
 * @param shortUrl   The complete shortened URL (base URL + code)
 * @param longUrl    The original long URL
 * @param createdAt  Timestamp when the URL was created
 * @param expiresAt  Optional expiration timestamp (null if never expires)
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Response containing the shortened URL information",
    example = """
        {
            "code": "abc123d",
            "shortUrl": "http://localhost:8080/r/abc123d",
            "longUrl": "https://www.example.com/very/long/url",
            "createdAt": "2026-01-29T10:30:00Z",
            "expiresAt": "2027-01-29T10:30:00Z"
        }
        """
)
public record UrlResponse(

    /**
     * The unique short code for this URL mapping.
     * This is the path segment used in the short URL.
     *
     * <p>Example: "abc123d"</p>
     */
    @Schema(
        description = "The unique short code identifying this URL",
        example = "abc123d"
    )
    String code,

    /**
     * The complete shortened URL that can be used directly.
     * Combines the base URL with the short code.
     *
     * <p>Example: "http://localhost:8080/r/abc123d"</p>
     */
    @Schema(
        description = "The complete shortened URL ready to use",
        example = "http://localhost:8080/r/abc123d"
    )
    String shortUrl,

    /**
     * The original long URL that was shortened.
     *
     * <p>Example: "https://www.example.com/very/long/path"</p>
     */
    @Schema(
        description = "The original long URL that was shortened",
        example = "https://www.example.com/very/long/url"
    )
    String longUrl,

    /**
     * Timestamp indicating when this URL mapping was created.
     * Uses ISO-8601 format in JSON serialization.
     *
     * <p>Example: "2026-01-27T10:30:00Z"</p>
     */
    @Schema(
        description = "Timestamp when the URL was created (ISO-8601 format)",
        example = "2026-01-29T10:30:00Z"
    )
    Instant createdAt,

    /**
     * Optional expiration timestamp for this URL mapping.
     * If null, the URL never expires.
     *
     * <p>Example: "2027-01-27T10:30:00Z"</p>
     */
    @Schema(
        description = "Optional expiration timestamp (ISO-8601 format). Null if URL never expires.",
        example = "2027-01-29T10:30:00Z",
        nullable = true
    )
    Instant expiresAt
) {

    /**
     * Checks if this URL has an expiration date.
     *
     * @return true if expiration is set, false otherwise
     */
    public boolean hasExpiration() {
        return expiresAt != null;
    }

    /**
     * Checks if this URL has expired based on the current time.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Returns the number of days until expiration.
     *
     * @return days until expiration, or -1 if no expiration is set
     */
    public long daysUntilExpiration() {
        if (expiresAt == null) {
            return -1;
        }
        long secondsUntilExpiration = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return secondsUntilExpiration / (24 * 60 * 60);
    }

    /**
     * Factory method to create a UrlResponse without expiration.
     *
     * @param code      the short code
     * @param shortUrl  the complete short URL
     * @param longUrl   the original long URL
     * @param createdAt the creation timestamp
     * @return a new UrlResponse instance
     */
    public static UrlResponse withoutExpiration(
            String code,
            String shortUrl,
            String longUrl,
            Instant createdAt) {
        return new UrlResponse(code, shortUrl, longUrl, createdAt, null);
    }

    /**
     * Factory method to create a UrlResponse with expiration.
     *
     * @param code      the short code
     * @param shortUrl  the complete short URL
     * @param longUrl   the original long URL
     * @param createdAt the creation timestamp
     * @param expiresAt the expiration timestamp
     * @return a new UrlResponse instance
     */
    public static UrlResponse withExpiration(
            String code,
            String shortUrl,
            String longUrl,
            Instant createdAt,
            Instant expiresAt) {
        return new UrlResponse(code, shortUrl, longUrl, createdAt, expiresAt);
    }
}
