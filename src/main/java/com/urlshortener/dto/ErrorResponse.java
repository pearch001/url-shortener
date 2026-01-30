package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description = "Error response containing details about what went wrong",
    example = """
        {
            "status": 400,
            "message": "Validation failed",
            "errors": ["URL is required", "Invalid URL format"],
            "timestamp": "2026-01-29T10:30:00Z",
            "path": "/api/shorten"
        }
        """
)
public class ErrorResponse {

    @Schema(
        description = "HTTP status code",
        example = "400"
    )
    private int status;

    @Schema(
        description = "Human-readable error message",
        example = "Validation failed"
    )
    private String message;

    @Schema(
        description = "List of detailed error messages",
        example = "[\"URL is required\", \"Invalid URL format\"]"
    )
    private List<String> errors;

    @Schema(
        description = "Timestamp when the error occurred (ISO-8601 format)",
        example = "2026-01-29T10:30:00Z"
    )
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(
        description = "Request path that caused the error",
        example = "/api/shorten"
    )
    private String path;
}
