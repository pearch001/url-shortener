package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO containing URL statistics and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description = "Response containing URL statistics and analytics",
    example = """
        {
            "shortCode": "abc123d",
            "originalUrl": "https://www.example.com/very/long/url",
            "accessCount": 42,
            "createdAt": "2026-01-29T10:30:00Z",
            "lastAccessedAt": "2026-01-29T15:45:00Z",
            "expiresAt": "2027-01-29T10:30:00Z"
        }
        """
)
public class UrlStatsResponse {

    @Schema(
        description = "The unique short code identifying this URL",
        example = "abc123d"
    )
    private String shortCode;

    @Schema(
        description = "The original long URL that was shortened",
        example = "https://www.example.com/very/long/url"
    )
    private String originalUrl;

    @Schema(
        description = "Total number of times this short URL has been accessed",
        example = "42"
    )
    private Long accessCount;

    @Schema(
        description = "Timestamp when the URL was created (ISO-8601 format)",
        example = "2026-01-29T10:30:00Z"
    )
    private Instant createdAt;

    @Schema(
        description = "Timestamp of the last access to this URL (ISO-8601 format). Null if never accessed.",
        example = "2026-01-29T15:45:00Z",
        nullable = true
    )
    private Instant lastAccessedAt;

    @Schema(
        description = "Optional expiration timestamp (ISO-8601 format). Null if URL never expires.",
        example = "2027-01-29T10:30:00Z",
        nullable = true
    )
    private Instant expiresAt;
}
