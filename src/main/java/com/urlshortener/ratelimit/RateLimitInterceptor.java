package com.urlshortener.ratelimit;

import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interceptor that enforces rate limiting using Bucket4j.
 * Implements token bucket algorithm for request throttling.
 *
 * <p><b>Algorithm: Token Bucket</b></p>
 * <ul>
 *   <li>Each client has a bucket with a maximum capacity (tokens)</li>
 *   <li>Each request consumes 1 token from the bucket</li>
 *   <li>Tokens refill at a constant rate</li>
 *   <li>If bucket is empty, request is rejected with 429</li>
 * </ul>
 *
 * <p><b>Storage:</b></p>
 * Currently uses in-memory ConcurrentHashMap for bucket storage.
 * For production with multiple instances, consider:
 * <ul>
 *   <li>Redis-backed Bucket4j (distributed rate limiting)</li>
 *   <li>Hazelcast for clustered in-memory storage</li>
 *   <li>API Gateway level rate limiting (AWS API Gateway, Kong, etc.)</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b></p>
 * ConcurrentHashMap ensures thread-safe access to buckets.
 * Bucket4j provides thread-safe token bucket implementation.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Component
@ConditionalOnProperty(name = "urlshortener.rate-limit.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties rateLimitProperties;

    /**
     * In-memory storage for client buckets.
     * Key: Client identifier (IP address, session ID, API key, etc.)
     * Value: Bucket4j bucket instance
     *
     * <p>Note: For production with multiple instances, replace with
     * distributed cache (Redis, Hazelcast, etc.)</p>
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Intercepts requests before they reach the controller.
     * Checks rate limit and rejects request if limit exceeded.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @param handler  the handler method
     * @return true if request is allowed, false if rate limited
     * @throws RateLimitExceededException if rate limit is exceeded
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        // Skip rate limiting if disabled globally
        if (!rateLimitProperties.isEnabled()) {
            log.debug("Rate limiting is disabled globally, skipping");
            return true;
        }

        // Only process if handler is a controller method
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Check if method has @RateLimited annotation
        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true; // No rate limiting configured
        }

        // Get client identifier based on strategy
        String clientId = getClientIdentifier(request, rateLimited.strategy());

        // Get or create bucket for this client
        Bucket bucket = resolveBucket(clientId, rateLimited);

        // Try to consume a token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Request allowed - add rate limit headers
            addRateLimitHeaders(response, rateLimited, probe);

            log.debug("Rate limit check passed for client: {} (remaining: {})",
                clientId, probe.getRemainingTokens());

            return true;

        } else {
            // Rate limit exceeded
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;

            // Add rate limit headers
            addRateLimitHeaders(response, rateLimited, probe);
            response.addHeader("Retry-After", String.valueOf(waitForRefill));

            log.warn("Rate limit exceeded for client: {} (retry after: {}s)",
                clientId, waitForRefill);

            // Throw exception to be handled by GlobalExceptionHandler
            throw RateLimitExceededException.forClient(
                clientId,
                rateLimited.maxRequests(),
                (int) rateLimited.durationSeconds(),
                waitForRefill
            );
        }
    }

    /**
     * Resolves or creates a bucket for the specified client.
     * Uses computeIfAbsent for thread-safe lazy initialization.
     *
     * @param clientId    the client identifier
     * @param rateLimited the rate limit configuration
     * @return Bucket instance for the client
     */
    private Bucket resolveBucket(String clientId, RateLimited rateLimited) {
        return buckets.computeIfAbsent(clientId, key -> createBucket(rateLimited));
    }

    /**
     * Creates a new Bucket4j bucket with the specified configuration.
     *
     * @param rateLimited the rate limit configuration
     * @return new Bucket instance
     */
    private Bucket createBucket(RateLimited rateLimited) {
        // Create bandwidth with refill strategy
        Refill refill = Refill.intervally(
            rateLimited.maxRequests(),
            Duration.ofSeconds(rateLimited.durationSeconds())
        );

        Bandwidth bandwidth = Bandwidth.classic(
            rateLimited.maxRequests(),
            refill
        );

        return Bucket.builder()
            .addLimit(bandwidth)
            .build();
    }

    /**
     * Extracts client identifier based on the specified strategy.
     *
     * @param request  the HTTP request
     * @param strategy the rate limiting strategy (ip, session, api-key, user)
     * @return client identifier string
     */
    private String getClientIdentifier(HttpServletRequest request, String strategy) {
        return switch (strategy.toLowerCase()) {
            case "ip" -> getClientIp(request);
            case "session" -> getSessionId(request);
            case "api-key" -> getApiKey(request);
            case "user" -> getUserId(request);
            default -> {
                log.warn("Unknown rate limit strategy: {}, falling back to IP", strategy);
                yield getClientIp(request);
            }
        };
    }

    /**
     * Extracts client IP address, handling proxy headers.
     *
     * @param request the HTTP request
     * @return client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
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
     * Extracts session ID from the request.
     *
     * @param request the HTTP request
     * @return session ID or "anonymous"
     */
    private String getSessionId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null ? session.getId() : "anonymous";
    }

    /**
     * Extracts API key from request header.
     *
     * @param request the HTTP request
     * @return API key or "no-api-key"
     */
    private String getApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        return apiKey != null ? apiKey : "no-api-key";
    }

    /**
     * Extracts user ID from authenticated principal.
     *
     * @param request the HTTP request
     * @return user ID or "anonymous"
     */
    private String getUserId(HttpServletRequest request) {
        var principal = request.getUserPrincipal();
        return principal != null ? principal.getName() : "anonymous";
    }

    /**
     * Adds rate limit information headers to the response.
     * Follows GitHub's rate limiting headers convention.
     *
     * @param response    the HTTP response
     * @param rateLimited the rate limit configuration
     * @param probe       the consumption probe with remaining tokens
     */
    private void addRateLimitHeaders(
            HttpServletResponse response,
            RateLimited rateLimited,
            ConsumptionProbe probe) {

        // X-RateLimit-Limit: Maximum requests allowed in the window
        response.addHeader("X-RateLimit-Limit",
            String.valueOf(rateLimited.maxRequests()));

        // X-RateLimit-Remaining: Tokens remaining in the bucket
        response.addHeader("X-RateLimit-Remaining",
            String.valueOf(probe.getRemainingTokens()));

        // X-RateLimit-Reset: Unix timestamp when bucket fully refills
        long resetTimeSeconds = System.currentTimeMillis() / 1000
            + rateLimited.durationSeconds();
        response.addHeader("X-RateLimit-Reset",
            String.valueOf(resetTimeSeconds));
    }

    /**
     * Clears all buckets. Useful for testing or administrative purposes.
     * Should not be called in production without proper authorization.
     */
    public void clearBuckets() {
        int size = buckets.size();
        buckets.clear();
        log.info("Cleared {} rate limit buckets", size);
    }

    /**
     * Returns the number of tracked clients.
     *
     * @return number of clients with rate limit buckets
     */
    public int getBucketCount() {
        return buckets.size();
    }
}
