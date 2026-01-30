package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for shortening a URL with optional custom alias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description = "Request payload for shortening a URL",
    example = """
        {
            "url": "https://www.example.com/very/long/url/that/needs/shortening",
            "customAlias": "my-link",
            "expirationDays": 30
        }
        """
)
public class ShortenUrlRequest {

    @Schema(
        description = "The URL to be shortened. Must be a valid HTTP or HTTPS URL.",
        example = "https://www.example.com/very/long/url/that/needs/shortening",
        requiredMode = Schema.RequiredMode.REQUIRED,
        maxLength = 2048
    )
    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?://)[^\\s/$.?#].[^\\s]*$",
        message = "Invalid URL format. Must start with http:// or https://"
    )
    private String url;

    @Schema(
        description = "Optional custom alias for the short URL. If not provided, a random code will be generated.",
        example = "my-link",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        minLength = 4,
        maxLength = 20,
        nullable = true
    )
    @Size(max = 20, min = 4, message = "Custom alias must be between 4 and 20 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]*$",
        message = "Custom alias can only contain letters, numbers, hyphens, and underscores"
    )
    private String customAlias;

    @Schema(
        description = "Number of days until the short URL expires. If not provided, uses default expiration.",
        example = "30",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        minimum = "1",
        maximum = "365",
        nullable = true
    )
    private Integer expirationDays;
}
