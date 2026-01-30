package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlMetadataResponse;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.observability.UrlMetricsService;
import com.urlshortener.ratelimit.RateLimited;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST controller for URL shortening operations.
 * Provides endpoints for creating and managing shortened URLs.
 *
 * <p><b>Base Path:</b> /api/urls</p>
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>POST /api/urls - Create a new shortened URL</li>
 *   <li>GET /api/urls/{code} - Retrieve URL metadata and statistics</li>
 *   <li>DELETE /api/urls/{code} - Delete a shortened URL</li>
 *   <li>PATCH /api/urls/{code}/expiration - Update expiration</li>
 * </ul>
 *
 * <p><b>Rate Limiting:</b></p>
 * POST endpoint is rate-limited to prevent abuse (configurable via application.yml).
 *
 * <p><b>Metrics:</b></p>
 * All operations are tracked via Micrometer metrics for monitoring and alerting.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping(
    value = "/api/urls",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
@Slf4j
@Tag(
    name = "URL Management",
    description = "APIs for creating, retrieving, and managing shortened URLs"
)
public class UrlController {

    private final UrlService urlService;
    private final UrlMetricsService metricsService;

    /**
     * Constructor with dependency injection for service and metrics.
     *
     * @param urlService     the URL shortening service
     * @param metricsService centralized metrics service
     */
    public UrlController(UrlService urlService, UrlMetricsService metricsService) {
        this.urlService = urlService;
        this.metricsService = metricsService;
    }

    /**
     * Creates a new shortened URL.
     *
     * <p><b>Idempotency:</b> Submitting the same URL multiple times returns
     * the same short code if it already exists and is not expired.</p>
     *
     * <p><b>Rate Limiting:</b> This endpoint is rate-limited. Exceeding the
     * limit returns 429 Too Many Requests.</p>
     *
     * @param request the URL creation request containing longUrl and optional expiresAt
     * @return ResponseEntity with UrlResponse and Location header (201 Created)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RateLimited(maxRequests = 10, durationSeconds = 60)
    @Operation(
        summary = "Create a shortened URL",
        description = "Creates a new shortened URL from a long URL. " +
                      "Returns existing code if the URL was already shortened (idempotent)."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "URL successfully shortened",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UrlResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "code": "abc123d",
                          "shortUrl": "http://localhost:8080/r/abc123d",
                          "longUrl": "https://www.example.com/very/long/path/to/resource",
                          "createdAt": "2026-01-27T10:30:00Z",
                          "expiresAt": "2027-01-27T10:30:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - validation failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Too many requests - rate limit exceeded",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    public ResponseEntity<UrlResponse> createShortUrl(
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "URL creation request",
                required = true,
                content = @Content(
                    examples = @ExampleObject(
                        value = """
                            {
                              "longUrl": "https://www.example.com/very/long/path/to/resource",
                              "expiresAt": "2027-01-27T10:30:00Z"
                            }
                            """
                    )
                )
            )
            CreateUrlRequest request) {

        log.info("POST /api/urls - Creating short URL for: {}", request.longUrl());

        UrlResponse response = urlService.createShortUrl(request);

        // Record metrics
        metricsService.recordUrlCreated();

        log.info("POST /api/urls - Created short URL: {} -> {}", response.code(), request.longUrl());

        return ResponseEntity
            .created(URI.create(response.shortUrl()))
            .body(response);
    }

    /**
     * Retrieves metadata and statistics for a shortened URL.
     *
     * @param code the short code (7 alphanumeric characters)
     * @return ResponseEntity with UrlMetadataResponse (200 OK)
     */
    @GetMapping("/{code}")
    @Operation(
        summary = "Get URL metadata",
        description = "Retrieves comprehensive metadata for a shortened URL including hit count and statistics."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Metadata retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UrlMetadataResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "code": "abc123d",
                          "longUrl": "https://www.example.com/very/long/path/to/resource",
                          "shortUrl": "http://localhost:8080/r/abc123d",
                          "createdAt": "2026-01-27T10:30:00Z",
                          "expiresAt": "2027-01-27T10:30:00Z",
                          "hitCount": 42
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Short code not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    public ResponseEntity<UrlMetadataResponse> getUrlMetadata(
            @PathVariable
            @Pattern(
                regexp = "^[a-zA-Z0-9]{4,12}$",
                message = "Code must be 4-12 alphanumeric characters"
            )
            @Parameter(
                description = "Short code of the URL",
                example = "abc123d",
                required = true
            )
            String code) {

        log.debug("GET /api/urls/{} - Retrieving metadata", code);

        UrlMetadataResponse response = urlService.getUrlMetadata(code);

        // Record metrics
        metricsService.recordUrlLookup();

        log.debug("GET /api/urls/{} - Metadata retrieved (hitCount: {})", code, response.hitCount());

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a shortened URL.
     *
     * @param code the short code to delete
     * @return ResponseEntity with no content (204 No Content)
     */
    @DeleteMapping("/{code}")
    @Operation(
        summary = "Delete a shortened URL",
        description = "Permanently deletes a shortened URL. This action cannot be undone."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "URL deleted successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Short code not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    public ResponseEntity<Void> deleteShortUrl(
            @PathVariable
            @Pattern(
                regexp = "^[a-zA-Z0-9]{4,12}$",
                message = "Code must be 4-12 alphanumeric characters"
            )
            @Parameter(
                description = "Short code of the URL to delete",
                example = "abc123d",
                required = true
            )
            String code) {

        log.info("DELETE /api/urls/{} - Deleting short URL", code);

        urlService.deleteShortUrl(code);

        // Record metrics
        metricsService.recordUrlDeleted();

        log.info("DELETE /api/urls/{} - Deleted successfully", code);

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the expiration time for a shortened URL.
     *
     * @param code      the short code
     * @param expiresAt new expiration time (ISO-8601 format)
     * @return ResponseEntity with no content (204 No Content)
     */
    @PatchMapping("/{code}/expiration")
    @Operation(
        summary = "Update URL expiration",
        description = "Updates the expiration time for a shortened URL. Set to null for permanent URLs."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Expiration updated successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Short code not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    public ResponseEntity<Void> updateExpiration(
            @PathVariable
            @Pattern(
                regexp = "^[a-zA-Z0-9]{4,12}$",
                message = "Code must be 4-12 alphanumeric characters"
            )
            @Parameter(
                description = "Short code of the URL",
                example = "abc123d",
                required = true
            )
            String code,

            @RequestParam(required = false)
            @Parameter(
                description = "New expiration timestamp (ISO-8601 format, null for permanent)",
                example = "2027-12-31T23:59:59Z"
            )
            java.time.Instant expiresAt) {

        log.info("PATCH /api/urls/{}/expiration - Updating expiration to: {}", code, expiresAt);

        urlService.updateExpiration(code, expiresAt);

        log.info("PATCH /api/urls/{}/expiration - Updated successfully", code);

        return ResponseEntity.noContent().build();
    }
}
