package com.urlshortener.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for rate limiting.
 * <p>
 * These properties are bound from the application configuration file (e.g., application.yml)
 * under the prefix {@code url-shortener.rate-limit}.
 * </p>
 *
 * <p>Example configuration:</p>
 * <pre>
 * url-shortener:
 *   rate-limit:
 *     enabled: true
 *     capacity: 10
 *     refill-tokens: 10
 *     refill-period-minutes: 1
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "urlshortener.rate-limit")
public class RateLimitProperties {

    /**
     * Whether rate limiting is enabled globally.
     * When disabled, rate limiting annotations are ignored.
     */
    private boolean enabled = false; // default to disabled unless explicitly enabled via config

    /**
     * The default maximum number of tokens (requests) the bucket can hold.
     * This defines the burst capacity.
     */
    private int capacity = 10;

    /**
     * The default number of tokens to refill during each refill period.
     */
    private long refillTokens = 10;

    /**
     * The default refill period in minutes.
     * Tokens are refilled at this interval.
     */
    private long refillPeriodMinutes = 1;
}
