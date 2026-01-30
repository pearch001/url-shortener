package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO containing comprehensive metadata about a shortened URL.
 * Used when retrieving detailed information about an existing URL mapping.
 *
 * <p>This response includes usage statistics and all metadata associated
 * with a shortened URL, making it suitable for analytics and monitoring.</p>
 *
 * <p>Example JSON response:
 * <pre>
 * {
 *   "code": "abc123d",
 *   "longUrl": "https://www.example.com/very/long/path",
 *   "shortUrl": "http://localhost:8080/r/abc123d",
 *   "createdAt": "2026-01-27T10:30:00Z",
 *   "expiresAt": "2027-01-27T10:30:00Z",
 *   "hitCount": 42
 * }
 * </pre>
 *
 * @param code       The unique short code identifying this URL mapping
 * @param longUrl    The original long URL
 * @param shortUrl   The complete shortened URL
 * @param createdAt  Timestamp when the URL was created
 * @param expiresAt  Optional expiration timestamp
 * @param hitCount   Number of times this short URL has been accessed
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Response containing URL metadata including usage statistics",
    example = """
        {
            "code": "abc123d",
            "longUrl": "https://www.example.com/very/long/url",
            "shortUrl": "http://localhost:8080/r/abc123d",
            "createdAt": "2026-01-29T10:30:00Z",
            "expiresAt": "2027-01-29T10:30:00Z",
            "hitCount": 42
        }
        """
)
public record UrlMetadataResponse(

    /**
     * The unique short code for this URL mapping.
     *
     * <p>Example: "abc123d"</p>
     */
    @Schema(
        description = "The unique short code identifying this URL",
        example = "abc123d"
    )
    String code,

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
     * The complete shortened URL.
     * This is provided for convenience and can be used directly.
     *
     * <p>Example: "http://localhost:8080/r/abc123d"</p>
     */
    @Schema(
        description = "The complete shortened URL ready to use",
        example = "http://localhost:8080/r/abc123d"
    )
    String shortUrl,

    /**
     * Timestamp indicating when this URL mapping was created.
     *
     * <p>Example: "2026-01-27T10:30:00Z"</p>
     */
    @Schema(
        description = "Timestamp when the URL was created (ISO-8601 format)",
        example = "2026-01-29T10:30:00Z"
    )
    Instant createdAt,

    /**
     * Optional expiration timestamp.
     * If null, the URL never expires.
     *
     * <p>Example: "2027-01-27T10:30:00Z"</p>
     */
    @Schema(
        description = "Optional expiration timestamp (ISO-8601 format). Null if URL never expires.",
        example = "2027-01-29T10:30:00Z",
        nullable = true
    )
    Instant expiresAt,

    /**
     * Number of times this shortened URL has been accessed (redirected).
     * This metric tracks the popularity and usage of the short URL.
     *
     * <p>Example: 42</p>
     */
    @Schema(
        description = "Number of times this short URL has been accessed",
        example = "42"
    )
    long hitCount
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
     * Checks if this URL has been accessed at least once.
     *
     * @return true if hitCount is greater than 0, false otherwise
     */
    public boolean hasBeenAccessed() {
        return hitCount > 0;
    }

    /**
     * Returns the age of this URL mapping in days.
     *
     * @return number of days since creation
     */
    public long ageInDays() {
        long secondsSinceCreation = Instant.now().getEpochSecond() - createdAt.getEpochSecond();
        return secondsSinceCreation / (24 * 60 * 60);
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
     * Calculates the average hits per day since creation.
     *
     * @return average daily hit count, or 0 if age is 0
     */
    public double averageHitsPerDay() {
        long age = ageInDays();
        if (age == 0) {
            return hitCount; // All hits occurred today
        }
        return (double) hitCount / age;
    }

    /**
     * Returns a human-readable status of this URL.
     *
     * @return status string ("active", "expired", "unused")
     */
    public String status() {
        if (isExpired()) {
            return "expired";
        }
        if (!hasBeenAccessed()) {
            return "unused";
        }
        return "active";
    }

    /**
     * Factory method to create metadata from a URL mapping entity.
     * Convenience method for service layer to entity mapping.
     *
     * @param code      the short code
     * @param longUrl   the original long URL
     * @param shortUrl  the complete short URL
     * @param createdAt the creation timestamp
     * @param expiresAt the expiration timestamp (nullable)
     * @param hitCount  the hit count
     * @return a new UrlMetadataResponse instance
     */
    public static UrlMetadataResponse of(
            String code,
            String longUrl,
            String shortUrl,
            Instant createdAt,
            Instant expiresAt,
            long hitCount) {
        return new UrlMetadataResponse(
            code,
            longUrl,
            shortUrl,
            createdAt,
            expiresAt,
            hitCount
        );
    }
}
