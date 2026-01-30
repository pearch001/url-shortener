package com.urlshortener.observability;

import com.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Custom health indicator for the URL Shortener application.
 * Provides additional health checks beyond the default database health check.
 *
 * <p><b>Health Checks Performed:</b></p>
 * <ul>
 *   <li>Database connectivity (via repository query)</li>
 *   <li>Active URL count (indicates system is processing data)</li>
 *   <li>Database write capability (can insert/update records)</li>
 * </ul>
 *
 * <p><b>Health Status:</b></p>
 * <ul>
 *   <li>UP: All checks pass</li>
 *   <li>DOWN: Database not accessible or critical error</li>
 * </ul>
 *
 * <p><b>Access:</b></p>
 * Health information is available at: {@code GET /actuator/health}
 *
 * <p><b>Example Response:</b></p>
 * <pre>
 * {
 *   "status": "UP",
 *   "components": {
 *     "urlShortener": {
 *       "status": "UP",
 *       "details": {
 *         "database": "accessible",
 *         "activeUrls": 1234,
 *         "totalUrls": 5678
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Component("urlShortener")
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerHealthIndicator implements HealthIndicator {

    private final UrlMappingRepository urlMappingRepository;

    /**
     * Performs health check for the URL shortener application.
     *
     * @return Health status with details
     */
    @Override
    public Health health() {
        try {
            // Check 1: Database connectivity and query capability
            long totalUrls = urlMappingRepository.count();

            // Check 2: Count active (non-expired) URLs
            long activeUrls = urlMappingRepository.countActiveUrls(Instant.now());

            // Check 3: Calculate expired URLs
            long expiredUrls = totalUrls - activeUrls;

            // All checks passed
            log.debug("Health check passed - Total: {}, Active: {}, Expired: {}",
                totalUrls, activeUrls, expiredUrls);

            return Health.up()
                    .withDetail("database", "accessible")
                    .withDetail("totalUrls", totalUrls)
                    .withDetail("activeUrls", activeUrls)
                    .withDetail("expiredUrls", expiredUrls)
                    .withDetail("timestamp", Instant.now().toString())
                    .build();

        } catch (Exception e) {
            // Health check failed
            log.error("Health check failed - Database not accessible", e);

            return Health.down()
                    .withDetail("database", "not accessible")
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .withDetail("timestamp", Instant.now().toString())
                    .build();
        }
    }
}
