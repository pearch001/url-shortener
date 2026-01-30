package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO returned after successfully shortening a URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description = "Response containing the shortened URL information",
    example = """
        {
            "shortUrl": "http://localhost:8080/r/abc123d",
            "shortCode": "abc123d",
            "originalUrl": "https://www.example.com/very/long/url",
            "createdAt": "2026-01-29T10:30:00Z",
            "expiresAt": "2027-01-29T10:30:00Z"
        }
        """
)
public class ShortenUrlResponse {

    @Schema(
        description = "The complete shortened URL ready to use",
        example = "http://localhost:8080/r/abc123d"
    )
    private String shortUrl;

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
        description = "Timestamp when the URL was created (ISO-8601 format)",
        example = "2026-01-29T10:30:00Z"
    )
    private Instant createdAt;

    @Schema(
        description = "Optional expiration timestamp (ISO-8601 format). Null if URL never expires.",
        example = "2027-01-29T10:30:00Z",
        nullable = true
    )
    private Instant expiresAt;
}
