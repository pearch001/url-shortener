package com.urlshortener.controller;

import com.urlshortener.observability.UrlMetricsService;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller for URL redirection.
 * Handles the core redirect functionality of the URL shortener.
 *
 * <p><b>Base Path:</b> /r</p>
 *
 * <p><b>Endpoint:</b></p>
 * <ul>
 *   <li>GET /r/{code} - Redirect to the original long URL</li>
 * </ul>
 *
 * <p><b>Performance:</b></p>
 * This is the hot path - highly optimized for low latency:
 * <ul>
 *   <li>Database lookup with indexed column (O(log n))</li>
 *   <li>Minimal object allocation</li>
 *   <li>Async metrics recording</li>
 *   <li>Target: &lt; 10ms response time (p95)</li>
 * </ul>
 *
 * <p><b>Metrics:</b></p>
 * All redirects are tracked with detailed metrics:
 * <ul>
 *   <li>Total redirect count</li>
 *   <li>Redirect latency (timer)</li>
 *   <li>Success/failure rates</li>
 *   <li>Tagged by result (success, not_found, expired)</li>
 * </ul>
 *
 * <p><b>HTTP Status Codes:</b></p>
 * <ul>
 *   <li>302 Found - Successful redirect (temporary redirect)</li>
 *   <li>404 Not Found - Short code doesn't exist</li>
 *   <li>410 Gone - URL has expired</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/r")
@Validated
@Slf4j
@Tag(
    name = "URL Redirection",
    description = "Redirects short URLs to their original destinations"
)
public class RedirectController {

    private final UrlService urlService;
    private final UrlMetricsService metricsService;

    /**
     * Constructor with dependency injection for service and metrics.
     *
     * @param urlService     the URL service
     * @param metricsService centralized metrics service
     */
    public RedirectController(UrlService urlService, UrlMetricsService metricsService) {
        this.urlService = urlService;
        this.metricsService = metricsService;
    }

    /**
     * Redirects a short code to its original long URL.
     *
     * <p><b>Operation:</b></p>
     * <ol>
     *   <li>Lookup URL mapping by short code</li>
     *   <li>Verify URL exists and is not expired</li>
     *   <li>Increment hit count (asynchronously)</li>
     *   <li>Return 302 Found with Location header</li>
     * </ol>
     *
     * <p><b>Performance Note:</b></p>
     * This method is optimized as the primary use case (hot path).
     * Average response time should be &lt; 10ms (p95).
     *
     * @param code    the short code to resolve
     * @param request the HTTP servlet request (for logging/metrics)
     * @return ResponseEntity with 302 Found status and Location header
     */
    @GetMapping("/{code}")
    @Operation(
        summary = "Redirect to original URL",
        description = "Redirects the short code to the original long URL. " +
                      "Returns 302 Found with Location header pointing to the original URL."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "302",
            description = "Redirect successful - Location header contains original URL",
            headers = @io.swagger.v3.oas.annotations.headers.Header(
                name = "Location",
                description = "The original long URL",
                schema = @Schema(type = "string", example = "https://www.example.com/very/long/path")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Short code not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "410",
            description = "URL has expired",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    public ResponseEntity<Void> redirect(
            @PathVariable
            @Pattern(
                regexp = "^[a-zA-Z0-9]{4,12}$",
                message = "Code must be 4-12 alphanumeric characters"
            )
            @Parameter(
                description = "Short code to redirect",
                example = "abc123d",
                required = true
            )
            String code,
            HttpServletRequest request) {

        // Record total redirect counter
        metricsService.recordRedirectTotal();

        // Log with client info for observability
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        log.debug("GET /r/{} - Redirect request from IP: {}, User-Agent: {}",
            code, clientIp, userAgent);

        // Time the redirect operation
        return metricsService.timeRedirect(() -> {
            try {
                // Resolve short code to long URL (includes hit count increment)
                String longUrl = urlService.resolveShortUrl(code);

                // Record success
                metricsService.recordRedirectSuccess();

                log.info("GET /r/{} - Redirecting to: {} (IP: {})", code, longUrl, clientIp);

                // Return 302 Found with Location header
                return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .header("Location", longUrl) // preserve original URL without URI re-encoding
                    .build();

            } catch (com.urlshortener.exception.UrlNotFoundException e) {
                // Record not found
                metricsService.recordRedirectNotFound();
                log.warn("GET /r/{} - Not found (IP: {})", code, clientIp);
                throw e;

            } catch (com.urlshortener.exception.UrlExpiredException e) {
                // Record expired
                metricsService.recordRedirectExpired();
                log.warn("GET /r/{} - Expired (IP: {})", code, clientIp);
                throw e;
            }
        });
    }

    /**
     * Extracts client IP address from the request.
     * Handles X-Forwarded-For header for proxied requests.
     *
     * @param request the HTTP servlet request
     * @return client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take first IP if multiple proxies
            return xForwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header (Nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fall back to remote address
        return request.getRemoteAddr();
    }

    /**
     * Health check endpoint for monitoring redirect service availability.
     * Returns 200 OK if the redirect service is operational.
     *
     * @return ResponseEntity with 200 OK status
     */
    @GetMapping("/health")
    @Operation(
        summary = "Redirect service health check",
        description = "Returns 200 OK if the redirect service is operational"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Service is healthy"
    )
    public ResponseEntity<Void> health() {
        return ResponseEntity.ok().build();
    }
}
