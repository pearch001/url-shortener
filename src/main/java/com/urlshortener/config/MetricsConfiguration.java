package com.urlshortener.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics using Micrometer.
 * Customizes metric collection and adds common tags to all metrics.
 *
 * <p><b>Metrics Exposed:</b></p>
 * <ul>
 *   <li>shortener.url.created - Count of URLs created</li>
 *   <li>shortener.url.lookup - Count of metadata lookups</li>
 *   <li>shortener.url.deleted - Count of URLs deleted</li>
 *   <li>shortener.redirect.total - Total redirect requests</li>
 *   <li>shortener.redirect.success - Successful redirects</li>
 *   <li>shortener.redirect.not_found - 404 redirects</li>
 *   <li>shortener.redirect.expired - 410 redirects (expired)</li>
 *   <li>shortener.redirect.duration - Redirect latency (timer)</li>
 * </ul>
 *
 * <p><b>Common Tags:</b></p>
 * All metrics are tagged with:
 * <ul>
 *   <li>application: url-shortener</li>
 *   <li>environment: Configured via spring.profiles.active</li>
 * </ul>
 *
 * <p><b>Prometheus Format:</b></p>
 * Metrics are exposed in Prometheus format at /actuator/prometheus
 * for scraping by monitoring systems.
 *
 * <p><b>Example Prometheus Queries:</b></p>
 * <pre>
 * # Rate of URL creations per second
 * rate(shortener_url_created_total[5m])
 *
 * # Redirect success rate
 * rate(shortener_redirect_success_total[5m]) / rate(shortener_redirect_total[5m])
 *
 * # 95th percentile redirect latency
 * histogram_quantile(0.95, shortener_redirect_duration_seconds_bucket)
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MetricsConfiguration {

    /**
     * Customizes the meter registry with common tags.
     * These tags are added to all metrics emitted by the application.
     *
     * @return MeterRegistryCustomizer that adds common tags
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            // Add application name tag
            registry.config().commonTags(
                Tags.of(
                    Tag.of("application", "url-shortener"),
                    Tag.of("service", "url-shortener-api")
                )
            );

            log.info("Metrics configuration initialized with common tags");
        };
    }

}
