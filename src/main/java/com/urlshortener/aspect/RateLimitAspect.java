package com.urlshortener.aspect;

import com.urlshortener.annotation.RateLimited;
import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect for enforcing rate limiting on methods annotated with {@link RateLimited}.
 * <p>
 * This aspect uses the token bucket algorithm (via Bucket4j) to limit the number of
 * requests from each client IP address. Buckets are stored in-memory using a
 * {@link ConcurrentHashMap}.
 * </p>
 *
 * <p>IP address extraction priority:</p>
 * <ol>
 *   <li>X-Forwarded-For header (for requests through proxies/load balancers)</li>
 *   <li>X-Real-IP header</li>
 *   <li>Remote address from the request</li>
 * </ol>
 *
 * @see RateLimited
 * @see RateLimitExceededException
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!test")
public class RateLimitAspect {

    private final RateLimitProperties rateLimitProperties;

    /**
     * In-memory cache of rate limit buckets per client IP.
     * Key: Client IP address
     * Value: Token bucket for that client
     */
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    /**
     * Intercepts methods annotated with {@link RateLimited} and enforces rate limiting.
     *
     * @param joinPoint    the join point representing the intercepted method
     * @param rateLimited  the rate limiting annotation
     * @return the result of the intercepted method
     * @throws Throwable if the method execution fails or rate limit is exceeded
     */
    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        // Skip rate limiting if disabled globally
        if (!rateLimitProperties.isEnabled()) {
            log.debug("Rate limiting is disabled globally, skipping");
            return joinPoint.proceed();
        }

        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);

        log.debug("Rate limiting check for IP: {}", clientIp);

        // Get or create bucket for this client
        Bucket bucket = bucketCache.computeIfAbsent(clientIp, k -> createBucket(rateLimited));

        // Try to consume a token
        if (bucket.tryConsume(1)) {
            log.debug("Rate limit check passed for IP: {}", clientIp);
            return joinPoint.proceed();
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            throw new RateLimitExceededException(
                "Too many requests. Please try again later.",
                clientIp,
                rateLimited.refillPeriodMinutes() * 60
            );
        }
    }

    /**
     * Creates a new token bucket based on the rate limiting configuration.
     *
     * @param rateLimited the rate limiting annotation containing the configuration
     * @return a new configured bucket
     */
    private Bucket createBucket(RateLimited rateLimited) {
        // Use annotation values, or fall back to properties if annotation uses defaults
        int capacity = rateLimited.capacity();
        long refillTokens = rateLimited.refillTokens();
        long refillPeriodMinutes = rateLimited.refillPeriodMinutes();

        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(
                refillTokens,
                Duration.ofMinutes(refillPeriodMinutes)
            )
            .build();

        log.debug("Creating bucket with capacity: {}, refill: {} tokens per {} minutes",
            capacity, refillTokens, refillPeriodMinutes);

        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    /**
     * Extracts the client IP address from the HTTP request.
     * <p>
     * Checks headers in the following order to handle requests through proxies:
     * </p>
     * <ol>
     *   <li>X-Forwarded-For - standard header for proxied requests</li>
     *   <li>X-Real-IP - alternative header used by some proxies</li>
     *   <li>Remote address - direct connection IP</li>
     * </ol>
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (for requests through proxies/load balancers)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one (original client)
            String ip = xForwardedFor.split(",")[0].trim();
            log.trace("Client IP extracted from X-Forwarded-For: {}", ip);
            return ip;
        }

        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            log.trace("Client IP extracted from X-Real-IP: {}", xRealIp);
            return xRealIp;
        }

        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        log.trace("Client IP extracted from remote address: {}", remoteAddr);
        return remoteAddr;
    }

    /**
     * Gets the current HTTP request from the request context.
     *
     * @return the current HTTP request
     * @throws IllegalStateException if no request is available in the context
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException("No request available in current context");
        }

        return attributes.getRequest();
    }

    /**
     * Clears all cached buckets.
     * Useful for testing or administrative operations.
     */
    public void clearCache() {
        log.info("Clearing rate limit bucket cache");
        bucketCache.clear();
    }

    /**
     * Gets the current number of cached buckets.
     *
     * @return the number of cached buckets
     */
    public int getCacheSize() {
        return bucketCache.size();
    }
}
