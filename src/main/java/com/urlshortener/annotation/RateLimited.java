package com.urlshortener.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable rate limiting on controller methods.
 * <p>
 * Rate limiting is enforced per client IP address using the token bucket algorithm.
 * When the rate limit is exceeded, a {@code RateLimitExceededException} is thrown,
 * which results in an HTTP 429 (Too Many Requests) response.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @PostMapping
 * @RateLimited(capacity = 10, refillTokens = 10, refillPeriodMinutes = 1)
 * public ResponseEntity<?> createResource() {
 *     // method implementation
 * }
 * }
 * </pre>
 *
 * @see com.urlshortener.aspect.RateLimitAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /**
     * The maximum number of tokens (requests) the bucket can hold.
     * This defines the burst capacity.
     *
     * @return the bucket capacity
     */
    int capacity() default 10;

    /**
     * The number of tokens to refill during each refill period.
     *
     * @return the number of tokens to refill
     */
    long refillTokens() default 10;

    /**
     * The refill period in minutes.
     * Tokens are refilled at this interval.
     *
     * @return the refill period in minutes
     */
    long refillPeriodMinutes() default 1;
}
