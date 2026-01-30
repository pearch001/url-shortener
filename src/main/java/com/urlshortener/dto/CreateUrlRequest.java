package com.urlshortener.dto;

import com.urlshortener.validation.ValidUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request DTO for creating a new shortened URL.
 * Uses Java record for immutability and reduced boilerplate.
 *
 * <p>This DTO includes comprehensive validation annotations to ensure
 * data integrity before processing the URL shortening request.</p>
 *
 * <p>Example valid request:
 * <pre>
 * {
 *   "longUrl": "https://www.example.com/very/long/path",
 *   "expiresAt": "2025-12-31T23:59:59Z"
 * }
 * </pre>
 *
 * @param longUrl    The original long URL to be shortened. Must be a valid HTTP/HTTPS URL.
 * @param expiresAt  Optional expiration timestamp. If null, uses default expiration period.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Schema(
    description = "Request payload for creating a shortened URL",
    example = """
        {
            "longUrl": "https://www.example.com/very/long/url/that/needs/shortening",
            "expiresAt": "2027-01-29T23:59:59Z"
        }
        """
)
public record CreateUrlRequest(

    /**
     * The original long URL to be shortened.
     *
     * <p>Validation constraints:
     * <ul>
     *   <li>Cannot be null or blank</li>
     *   <li>Must be between 10 and 2048 characters</li>
     *   <li>Must be a valid HTTP or HTTPS URL</li>
     *   <li>Must contain a valid host component</li>
     * </ul>
     */
    @Schema(
        description = "The long URL to be shortened. Must be a valid HTTP or HTTPS URL.",
        example = "https://www.example.com/very/long/url/that/needs/shortening",
        requiredMode = Schema.RequiredMode.REQUIRED,
        maxLength = 2048,
        minLength = 10
    )
    @NotBlank(message = "Long URL is required and cannot be blank")
    @Size(min = 10, max = 2048, message = "Long URL must be between 10 and 2048 characters")
    @ValidUrl(
        message = "Invalid URL format. Must be a valid HTTP or HTTPS URL",
        protocols = {"http", "https"},
        maxLength = 2048,
        minLength = 10
    )
    String longUrl,

    /**
     * Optional expiration timestamp for the shortened URL.
     *
     * <p>If not provided, the system will use the default expiration
     * period configured in application properties.</p>
     *
     * <p>Notes:
     * <ul>
     *   <li>Must be a future timestamp if provided</li>
     *   <li>Validation for future dates should be done at service layer</li>
     *   <li>Null value indicates use of default expiration</li>
     * </ul>
     */
    @Schema(
        description = "Optional expiration time for the short URL in ISO-8601 format. If not provided, uses default expiration period.",
        example = "2027-01-29T23:59:59Z",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true
    )
    Instant expiresAt
) {

    /**
     * Compact constructor with additional validation logic.
     * Ensures expiresAt is not in the past if provided.
     *
     * @param longUrl   the long URL to validate
     * @param expiresAt the expiration timestamp to validate
     * @throws IllegalArgumentException if expiresAt is in the past
     */
    public CreateUrlRequest {
        // Additional validation: ensure expiresAt is in the future if provided
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                "Expiration time must be in the future. Provided: " + expiresAt);
        }
    }

    /**
     * Checks if this request has an expiration time specified.
     *
     * @return true if expiresAt is not null, false otherwise
     */
    public boolean hasExpiration() {
        return expiresAt != null;
    }

    /**
     * Returns a normalized version of the long URL (trimmed).
     *
     * @return normalized long URL
     */
    public String normalizedLongUrl() {
        return longUrl != null ? longUrl.trim() : null;
    }
}
