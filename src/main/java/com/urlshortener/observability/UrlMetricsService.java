package com.urlshortener.observability;

import com.urlshortener.repository.UrlMappingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics service for URL shortener business metrics.
 * Provides a clean API for recording metrics across the application.
 *
 * <p><b>Metrics Provided:</b></p>
 * <ul>
 *   <li>shortener.url.created - Count of URLs created</li>
 *   <li>shortener.url.lookup - Count of metadata lookups</li>
 *   <li>shortener.url.deleted - Count of URLs deleted</li>
 *   <li>shortener.url.updated - Count of URLs updated</li>
 *   <li>shortener.redirect.total - Total redirect requests</li>
 *   <li>shortener.redirect.success - Successful redirects</li>
 *   <li>shortener.redirect.not_found - 404 redirects</li>
 *   <li>shortener.redirect.expired - 410 redirects (expired)</li>
 *   <li>shortener.redirect.duration - Redirect latency (timer)</li>
 *   <li>shortener.code.collision - Code generation collisions</li>
 *   <li>shortener.url.active - Active URLs (gauge)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * &#64;RestController
 * public class UrlController {
 *     private final UrlMetricsService metrics;
 *
 *     &#64;PostMapping("/api/urls")
 *     public ResponseEntity&lt;UrlResponse&gt; createUrl(...) {
 *         UrlResponse response = urlService.createShortUrl(request);
 *         metrics.recordUrlCreated();
 *         return ResponseEntity.ok(response);
 *     }
 * }
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Component
@Slf4j
public class UrlMetricsService {

    private final MeterRegistry meterRegistry;
    private final UrlMappingRepository urlMappingRepository;

    // Counters
    private final Counter urlCreatedCounter;
    private final Counter urlLookupCounter;
    private final Counter urlDeletedCounter;
    private final Counter urlUpdatedCounter;
    private final Counter redirectTotalCounter;
    private final Counter redirectSuccessCounter;
    private final Counter redirectNotFoundCounter;
    private final Counter redirectExpiredCounter;
    private final Counter codeCollisionCounter;

    // Timers
    private final Timer redirectTimer;

    // Gauges
    private final AtomicLong activeUrlsCache;

    public UrlMetricsService(MeterRegistry meterRegistry, UrlMappingRepository urlMappingRepository) {
        this.meterRegistry = meterRegistry;
        this.urlMappingRepository = urlMappingRepository;

        // Initialize counters
        this.urlCreatedCounter = Counter.builder("shortener.url.created")
                .description("Total number of URLs created")
                .register(meterRegistry);

        this.urlLookupCounter = Counter.builder("shortener.url.lookup")
                .description("Total number of URL metadata lookups")
                .register(meterRegistry);

        this.urlDeletedCounter = Counter.builder("shortener.url.deleted")
                .description("Total number of URLs deleted")
                .register(meterRegistry);

        this.urlUpdatedCounter = Counter.builder("shortener.url.updated")
                .description("Total number of URLs updated")
                .register(meterRegistry);

        this.redirectTotalCounter = Counter.builder("shortener.redirect.total")
                .description("Total number of redirect requests")
                .register(meterRegistry);

        this.redirectSuccessCounter = Counter.builder("shortener.redirect.success")
                .description("Number of successful redirects")
                .register(meterRegistry);

        this.redirectNotFoundCounter = Counter.builder("shortener.redirect.not_found")
                .description("Number of 404 redirects (code not found)")
                .register(meterRegistry);

        this.redirectExpiredCounter = Counter.builder("shortener.redirect.expired")
                .description("Number of 410 redirects (expired URLs)")
                .register(meterRegistry);

        this.codeCollisionCounter = Counter.builder("shortener.code.collision")
                .description("Number of code generation collisions")
                .register(meterRegistry);

        // Initialize timers
        this.redirectTimer = Timer.builder("shortener.redirect.duration")
                .description("Time taken to process redirect requests")
                .publishPercentileHistogram()
                .register(meterRegistry);

        // Initialize gauges
        this.activeUrlsCache = new AtomicLong(0);
        Gauge.builder("shortener.url.active", activeUrlsCache, AtomicLong::get)
                .description("Number of active (non-expired) URLs")
                .register(meterRegistry);

        // Update active URL count on startup
        updateActiveUrlCount();

        log.info("UrlMetricsService initialized with all business metrics");
    }

    /**
     * Records a URL creation event.
     */
    public void recordUrlCreated() {
        urlCreatedCounter.increment();
        updateActiveUrlCount();
        log.debug("Metric recorded: URL created");
    }

    /**
     * Records a URL metadata lookup event.
     */
    public void recordUrlLookup() {
        urlLookupCounter.increment();
        log.debug("Metric recorded: URL lookup");
    }

    /**
     * Records a URL deletion event.
     */
    public void recordUrlDeleted() {
        urlDeletedCounter.increment();
        updateActiveUrlCount();
        log.debug("Metric recorded: URL deleted");
    }

    /**
     * Records a URL update event.
     */
    public void recordUrlUpdated() {
        urlUpdatedCounter.increment();
        log.debug("Metric recorded: URL updated");
    }

    /**
     * Records a redirect request (total).
     */
    public void recordRedirectTotal() {
        redirectTotalCounter.increment();
        log.debug("Metric recorded: Redirect total");
    }

    /**
     * Records a successful redirect.
     */
    public void recordRedirectSuccess() {
        redirectSuccessCounter.increment();
        log.debug("Metric recorded: Redirect success");
    }

    /**
     * Records a 404 redirect (code not found).
     */
    public void recordRedirectNotFound() {
        redirectNotFoundCounter.increment();
        log.debug("Metric recorded: Redirect not found");
    }

    /**
     * Records a 410 redirect (expired URL).
     */
    public void recordRedirectExpired() {
        redirectExpiredCounter.increment();
        log.debug("Metric recorded: Redirect expired");
    }

    /**
     * Records a code generation collision.
     */
    public void recordCodeCollision() {
        codeCollisionCounter.increment();
        log.debug("Metric recorded: Code collision");
    }

    /**
     * Times a redirect operation.
     *
     * @param operation the redirect operation to time
     * @param <T>       the return type of the operation
     * @return the result of the operation
     */
    public <T> T timeRedirect(java.util.function.Supplier<T> operation) {
        return redirectTimer.record(operation);
    }

    /**
     * Updates the active URL count gauge.
     * This is an expensive operation and should be called sparingly.
     */
    public void updateActiveUrlCount() {
        try {
            long count = urlMappingRepository.countActiveUrls(Instant.now());
            activeUrlsCache.set(count);
            log.debug("Active URL count updated: {}", count);
        } catch (Exception e) {
            log.error("Failed to update active URL count", e);
        }
    }

    /**
     * Returns the timer for redirect operations.
     * Useful for manual timing when the timeRedirect method doesn't fit the use case.
     *
     * @return the redirect timer
     */
    public Timer getRedirectTimer() {
        return redirectTimer;
    }

    /**
     * Returns the meter registry for custom metrics.
     *
     * @return the meter registry
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
