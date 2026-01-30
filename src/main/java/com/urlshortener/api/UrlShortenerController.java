package com.urlshortener.api;

import com.urlshortener.dto.ShortenUrlRequest;
import com.urlshortener.dto.ShortenUrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for URL shortening operations.
 * Provides endpoints to shorten URLs, redirect, retrieve statistics, and delete URLs.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(
    name = "URL Shortener",
    description = "API for URL shortening operations including create, redirect, stats, and delete"
)
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    /**
     * Creates a shortened URL from a long URL.
     *
     * @param request the URL shortening request
     * @return the shortened URL response
     */
    @PostMapping("/api/shorten")
    @Operation(
        summary = "Shorten a URL",
        description = """
            Creates a shortened URL from a long URL.

            **Features:**
            - Generate random short code or use custom alias
            - Set optional expiration time
            - Returns 201 Created on success

            **Rate Limiting:**
            This endpoint is rate limited. Exceeding the limit will return 429 Too Many Requests.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "URL shortened successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ShortenUrlResponse.class),
                examples = @ExampleObject(
                    name = "Success",
                    value = """
                        {
                            "shortUrl": "http://localhost:8080/r/abc123d",
                            "shortCode": "abc123d",
                            "originalUrl": "https://www.example.com/very/long/url",
                            "createdAt": "2026-01-29T10:30:00Z",
                            "expiresAt": "2027-01-29T10:30:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid URL format or validation failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(
                    name = "Validation Error",
                    value = """
                        {
                            "type": "about:blank",
                            "title": "Bad Request",
                            "status": 400,
                            "detail": "Invalid URL format. Must start with http:// or https://",
                            "instance": "/api/shorten"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Custom alias already exists",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(
                    name = "Conflict",
                    value = """
                        {
                            "type": "about:blank",
                            "title": "Conflict",
                            "status": 409,
                            "detail": "Custom alias 'my-link' already exists",
                            "instance": "/api/shorten"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded",
            headers = {
                @Header(
                    name = "X-RateLimit-Limit",
                    description = "Maximum number of requests allowed",
                    schema = @Schema(type = "integer", example = "10")
                ),
                @Header(
                    name = "X-RateLimit-Remaining",
                    description = "Number of requests remaining",
                    schema = @Schema(type = "integer", example = "0")
                ),
                @Header(
                    name = "X-RateLimit-Reset",
                    description = "Time in seconds until rate limit resets",
                    schema = @Schema(type = "integer", example = "60")
                )
            },
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(
                    name = "Rate Limited",
                    value = """
                        {
                            "type": "about:blank",
                            "title": "Too Many Requests",
                            "status": 429,
                            "detail": "Rate limit exceeded. Please try again later.",
                            "instance": "/api/shorten"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<ShortenUrlResponse> shortenUrl(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "URL shortening request payload",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ShortenUrlRequest.class),
                    examples = @ExampleObject(
                        name = "Create Short URL",
                        value = """
                            {
                                "url": "https://www.example.com/very/long/url/that/needs/shortening",
                                "customAlias": "my-link",
                                "expirationDays": 30
                            }
                            """
                    )
                )
            )
            @Valid @RequestBody ShortenUrlRequest request) {
        log.info("POST /api/shorten - Request: {}", request.getUrl());
        ShortenUrlResponse response = urlShortenerService.shortenUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==================== Statistics and Management ====================

    /**
     * Retrieves statistics for a shortened URL.
     *
     * @param shortCode the short code to get stats for
     * @return the URL statistics response
     */
    @GetMapping("/api/stats/{shortCode}")
    @Operation(
        summary = "Get URL statistics",
        description = """
            Retrieves statistics and analytics for a shortened URL.

            **Statistics included:**
            - Access count (number of redirects)
            - Creation timestamp
            - Last accessed timestamp
            - Expiration timestamp (if set)
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Statistics retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UrlStatsResponse.class),
                examples = @ExampleObject(
                    name = "Success",
                    value = """
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
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Short URL not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(
                    name = "Not Found",
                    value = """
                        {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "Short URL with code 'abc123d' not found",
                            "instance": "/api/stats/abc123d"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<UrlStatsResponse> getUrlStats(
            @Parameter(
                description = "The short code to get statistics for",
                example = "abc123d",
                required = true
            )
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_-]{4,20}$", message = "Invalid short code format")
            String shortCode) {
        log.info("GET /api/stats/{} - Retrieving stats", shortCode);
        UrlStatsResponse stats = urlShortenerService.getUrlStats(shortCode);
        return ResponseEntity.ok(stats);
    }

    /**
     * Deletes a shortened URL by its short code.
     *
     * @param shortCode the short code to delete
     * @return no content response
     */
    @DeleteMapping("/api/urls/{shortCode}")
    @Operation(
        summary = "Delete a shortened URL",
        description = """
            Deletes a shortened URL by its short code.

            **Behavior:**
            - Permanently removes the URL mapping
            - Returns 204 No Content on success
            - Returns 404 if code not found
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "URL deleted successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Short URL not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(
                    name = "Not Found",
                    value = """
                        {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "Short URL with code 'abc123d' not found",
                            "instance": "/api/urls/abc123d"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<Void> deleteUrl(
            @Parameter(
                description = "The short code of the URL to delete",
                example = "abc123d",
                required = true
            )
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9_-]{4,20}$", message = "Invalid short code format")
            String shortCode) {
        log.info("DELETE /api/urls/{} - Deleting URL", shortCode);
        urlShortenerService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
