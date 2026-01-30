package com.urlshortener.scheduler;

import com.urlshortener.config.UrlShortenerProperties;
import com.urlshortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled task for cleaning up expired URL mappings.
 * Runs periodically based on configuration to remove URLs that have passed their expiration date.
 *
 * <p><b>Purpose:</b></p>
 * <ul>
 *   <li>Prevent database bloat from expired URLs</li>
 *   <li>Maintain system performance</li>
 *   <li>Ensure expired URLs are not accessible</li>
 *   <li>Free up short codes for reuse (though collision is rare)</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * This scheduler is enabled/disabled and configured via application.yml:
 * <pre>
 * url-shortener:
 *   cleanup:
 *     enabled: true
 *     cron: "0 0 0 * * *"  # Daily at midnight
 *     batch-size: 1000
 * </pre>
 *
 * <p><b>Scheduling:</b></p>
 * The cleanup job uses Spring's @Scheduled annotation with a cron expression.
 * Default: Runs daily at midnight (00:00:00).
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Runs during low-traffic hours (typically midnight)</li>
 *   <li>Uses efficient bulk DELETE query</li>
 *   <li>Transaction ensures data consistency</li>
 *   <li>Batch size prevents long-running transactions</li>
 * </ul>
 *
 * <p><b>Monitoring:</b></p>
 * Each cleanup execution is logged with:
 * <ul>
 *   <li>Start timestamp</li>
 *   <li>Number of URLs deleted</li>
 *   <li>Execution duration</li>
 *   <li>Any errors encountered</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Component
@ConditionalOnProperty(
    prefix = "url-shortener.cleanup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class UrlCleanupScheduler {

    private final UrlService urlService;
    private final UrlShortenerProperties properties;

    /**
     * Scheduled task that cleans up expired URL mappings.
     * Runs according to the cron expression configured in application.yml.
     *
     * <p><b>Execution Flow:</b></p>
     * <ol>
     *   <li>Log cleanup start</li>
     *   <li>Call UrlService.cleanupExpiredUrls()</li>
     *   <li>Log number of URLs deleted</li>
     *   <li>Log execution duration</li>
     *   <li>Handle any exceptions</li>
     * </ol>
     *
     * <p><b>Error Handling:</b></p>
     * Any exceptions during cleanup are caught and logged. The scheduler continues
     * to run on subsequent triggers even if an execution fails.
     *
     * <p><b>Cron Expression Examples:</b></p>
     * <ul>
     *   <li>{@code "0 0 0 * * *"} - Daily at midnight</li>
     *   <li>{@code "0 0 2 * * *"} - Daily at 2 AM</li>
     *   <li>{@code "0 0 0 * * SUN"} - Weekly on Sunday at midnight</li>
     * </ul>
     */
    @Scheduled(cron = "${url-shortener.cleanup.cron:0 0 0 * * *}")
    public void cleanupExpiredUrls() {
        log.info("=== Starting scheduled URL cleanup job ===");
        long startTime = System.currentTimeMillis();
        Instant startInstant = Instant.now();

        try {
            // Execute cleanup
            int deletedCount = urlService.cleanupExpiredUrls();

            long duration = System.currentTimeMillis() - startTime;

            // Log results
            if (deletedCount > 0) {
                log.info("Cleanup job completed successfully:");
                log.info("  - Deleted URLs: {}", deletedCount);
                log.info("  - Duration: {}ms", duration);
                log.info("  - Timestamp: {}", startInstant);
            } else {
                log.info("Cleanup job completed: no expired URLs found (duration: {}ms)", duration);
            }

            // Log statistics if significant cleanup occurred
            if (deletedCount > 100) {
                log.info("Large cleanup detected ({} URLs). Consider reviewing expiration settings.", deletedCount);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Cleanup job failed after {}ms", duration, e);
            log.error("Error details: {}", e.getMessage());

            // Don't rethrow - allow scheduler to continue on next trigger
            // Consider sending alert to monitoring system here
        }

        log.info("=== Scheduled URL cleanup job finished ===");
    }

    /**
     * Manual cleanup trigger for testing or administrative purposes.
     * Can be called via JMX or custom admin endpoint.
     *
     * <p>This method bypasses the scheduler and runs cleanup immediately.
     * Useful for:
     * <ul>
     *   <li>Testing the cleanup logic</li>
     *   <li>Manual administrative cleanup</li>
     *   <li>One-time bulk cleanup operations</li>
     * </ul>
     *
     * @return number of URLs deleted
     */
    public int triggerManualCleanup() {
        log.info("Manual cleanup triggered");
        try {
            int deleted = urlService.cleanupExpiredUrls();
            log.info("Manual cleanup completed: deleted {} URL(s)", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("Manual cleanup failed", e);
            throw new RuntimeException("Manual cleanup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns information about the next scheduled cleanup.
     * Useful for monitoring and admin dashboards.
     *
     * @return cleanup configuration details
     */
    public String getCleanupInfo() {
        return String.format(
            "Cleanup scheduler: enabled=%s, cron='%s', batchSize=%d",
            properties.getCleanup().isEnabled(),
            properties.getCleanup().getCron(),
            properties.getCleanup().getBatchSize()
        );
    }

    /**
     * Checks if the cleanup scheduler is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return properties.getCleanup().isEnabled();
    }
}
