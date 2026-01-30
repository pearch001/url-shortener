package com.urlshortener.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable rate limiting on controller methods.
 * Uses Bucket4j for token bucket algorithm implementation.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * &#64;PostMapping("/api/urls")
 * &#64;RateLimited(maxRequests = 10, durationSeconds = 60)
 * public ResponseEntity&lt;UrlResponse&gt; createShortUrl(...) {
 *     // Method implementation
 * }
 * </pre>
 *
 * <p><b>Rate Limiting Strategy:</b></p>
 * By default, rate limiting is applied per client IP address.
 * This can be configured via the strategy parameter.
 *
 * <p><b>Token Bucket Algorithm:</b></p>
 * <ul>
 *   <li>Bucket capacity = maxRequests</li>
 *   <li>Refill rate = maxRequests / durationSeconds</li>
 *   <li>Each request consumes 1 token</li>
 *   <li>If bucket is empty, request is rejected with 429</li>
 * </ul>
 *
 * <p><b>Response Headers:</b></p>
 * When rate limiting is applied, these headers are added:
 * <ul>
 *   <li>X-RateLimit-Limit: Maximum requests allowed</li>
 *   <li>X-RateLimit-Remaining: Tokens remaining in bucket</li>
 *   <li>X-RateLimit-Reset: Unix timestamp when bucket refills</li>
 *   <li>Retry-After: Seconds to wait before retry (when rate limited)</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 * @see RateLimitInterceptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /**
     * Maximum number of requests allowed within the duration window.
     * This is the capacity of the token bucket.
     *
     * <p>Example: maxRequests = 10 means 10 requests per duration</p>
     *
     * @return maximum request count
     */
    int maxRequests() default 100;

    /**
     * Duration in seconds for the rate limit window.
     * Tokens refill at a rate of maxRequests / durationSeconds.
     *
     * <p>Example: durationSeconds = 60 means per-minute rate limiting</p>
     *
     * @return duration in seconds
     */
    long durationSeconds() default 60;

    /**
     * Rate limiting strategy to use.
     *
     * <p>Options:</p>
     * <ul>
     *   <li>ip: Limit by client IP address (default)</li>
     *   <li>session: Limit by HTTP session ID</li>
     *   <li>api-key: Limit by API key header</li>
     *   <li>user: Limit by authenticated user ID</li>
     * </ul>
     *
     * @return rate limit strategy
     */
    String strategy() default "ip";

    /**
     * Whether to skip rate limiting for authenticated users.
     * Useful to provide higher limits for registered users.
     *
     * @return true to skip rate limiting for authenticated users
     */
    boolean skipAuthenticated() default false;
}
