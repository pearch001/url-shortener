package com.urlshortener.integration;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Spring Boot Actuator endpoints.
 * Tests health checks, metrics, and observability features.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Actuator Endpoints Integration Tests")
class ActuatorEndpointsTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UrlMappingRepository repository;

    private String baseUrl;

    /**
     * TestRestTemplate configured to NOT follow redirects.
     * Used for testing redirect responses (302).
     */
    private TestRestTemplate noRedirectRestTemplate;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;
        repository.deleteAll();

        // Create a TestRestTemplate that doesn't follow redirects
        noRedirectRestTemplate = new TestRestTemplate(
            new RestTemplateBuilder()
                .requestFactory(() -> {
                    return new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                        @Override
                        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                            super.prepareConnection(connection, httpMethod);
                            connection.setInstanceFollowRedirects(false);
                        }
                    };
                })
        );
    }

    // ==================== Health Endpoint Tests ====================

    @Test
    @DisplayName("Should expose health endpoint")
    void shouldExposeHealthEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/health",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("Should show database health in health endpoint")
    void shouldShowDatabaseHealth() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/health",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Check for components if detailed health is exposed
        if (response.getBody().containsKey("components")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
            assertThat(components).containsKey("db");
        }
    }

    @Test
    @DisplayName("Should show disk space health")
    void shouldShowDiskSpaceHealth() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/health",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Check for components if detailed health is exposed
        if (response.getBody().containsKey("components")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
            assertThat(components).containsKey("diskSpace");
        }
    }

    @Test
    @DisplayName("Should return 200 for ping endpoint")
    void shouldReturnOkForPing() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/health/ping",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    // ==================== Info Endpoint Tests ====================

    @Test
    @DisplayName("Should expose info endpoint")
    void shouldExposeInfoEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/info",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    // ==================== Metrics Endpoint Tests ====================

    @Test
    @DisplayName("Should expose metrics endpoint")
    void shouldExposeMetricsEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("names");

        @SuppressWarnings("unchecked")
        java.util.List<String> metricNames = (java.util.List<String>) response.getBody().get("names");
        assertThat(metricNames).isNotEmpty();
    }

    @Test
    @DisplayName("Should expose JVM memory metrics")
    void shouldExposeJvmMemoryMetrics() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/jvm.memory.used",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("name");
        assertThat(response.getBody().get("name")).isEqualTo("jvm.memory.used");
        assertThat(response.getBody()).containsKey("measurements");
    }

    @Test
    @DisplayName("Should expose HTTP server requests metrics")
    void shouldExposeHttpServerMetrics() {
        // Given - Make a request to generate metrics
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/test", null);
        restTemplate.postForEntity(baseUrl + "/api/urls", request, UrlResponse.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/http.server.requests",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("http.server.requests");
    }

    @Test
    @DisplayName("Should track custom shortener URL created metric")
    void shouldTrackCustomUrlCreatedMetric() {
        // Given - Create some URLs
        CreateUrlRequest request1 = new CreateUrlRequest("https://example.com/metric1", null);
        CreateUrlRequest request2 = new CreateUrlRequest("https://example.com/metric2", null);

        restTemplate.postForEntity(baseUrl + "/api/urls", request1, UrlResponse.class);
        restTemplate.postForEntity(baseUrl + "/api/urls", request2, UrlResponse.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.url.created",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.url.created");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> measurements =
            (java.util.List<Map<String, Object>>) response.getBody().get("measurements");

        assertThat(measurements).isNotEmpty();

        // Find the COUNT measurement
        Map<String, Object> countMeasurement = measurements.stream()
            .filter(m -> "COUNT".equals(m.get("statistic")))
            .findFirst()
            .orElse(null);

        assertThat(countMeasurement).isNotNull();
        assertThat(((Number) countMeasurement.get("value")).doubleValue()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    @DisplayName("Should track custom shortener redirect total metric")
    void shouldTrackCustomRedirectTotalMetric() {
        // Given - Create and redirect
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/redirect-metric", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        // Perform redirects
        String code = createResponse.code();
        restTemplate.exchange(baseUrl + "/r/" + code, HttpMethod.GET, null, Void.class);
        restTemplate.exchange(baseUrl + "/r/" + code, HttpMethod.GET, null, Void.class);
        restTemplate.exchange(baseUrl + "/r/" + code, HttpMethod.GET, null, Void.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.redirect.total",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.redirect.total");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> measurements =
            (java.util.List<Map<String, Object>>) response.getBody().get("measurements");

        Map<String, Object> countMeasurement = measurements.stream()
            .filter(m -> "COUNT".equals(m.get("statistic")))
            .findFirst()
            .orElse(null);

        assertThat(countMeasurement).isNotNull();
        assertThat(((Number) countMeasurement.get("value")).doubleValue()).isGreaterThanOrEqualTo(3.0);
    }

    @Test
    @DisplayName("Should track custom shortener redirect success metric")
    void shouldTrackCustomRedirectSuccessMetric() {
        // Given - Create and redirect successfully
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/success-metric", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        restTemplate.exchange(baseUrl + "/r/" + createResponse.code(), HttpMethod.GET, null, Void.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.redirect.success",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.redirect.success");
    }

    @Test
    @DisplayName("Should track custom shortener redirect not found metric")
    void shouldTrackCustomRedirectNotFoundMetric() {
        // Given - Try to redirect with non-existent code
        restTemplate.exchange(baseUrl + "/r/notfound", HttpMethod.GET, null, String.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.redirect.not_found",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.redirect.not_found");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> measurements =
            (java.util.List<Map<String, Object>>) response.getBody().get("measurements");

        Map<String, Object> countMeasurement = measurements.stream()
            .filter(m -> "COUNT".equals(m.get("statistic")))
            .findFirst()
            .orElse(null);

        assertThat(countMeasurement).isNotNull();
        assertThat(((Number) countMeasurement.get("value")).doubleValue()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Should track custom shortener redirect latency metric")
    void shouldTrackCustomRedirectLatencyMetric() {
        // Given - Create and redirect
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/latency-metric", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        noRedirectRestTemplate.exchange(baseUrl + "/r/" + createResponse.code(), HttpMethod.GET, null, Void.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.redirect.duration",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.redirect.duration");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> measurements =
            (java.util.List<Map<String, Object>>) response.getBody().get("measurements");

        assertThat(measurements).isNotEmpty();

        // Should have COUNT, TOTAL_TIME, MAX measurements
        assertThat(measurements).extracting(m -> m.get("statistic"))
            .contains("COUNT", "TOTAL_TIME", "MAX");
    }

    @Test
    @DisplayName("Should track URL lookup metric")
    void shouldTrackUrlLookupMetric() {
        // Given - Create and lookup metadata
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/lookup-metric", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        restTemplate.getForEntity(baseUrl + "/api/urls/" + createResponse.code(), Map.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.url.lookup",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.url.lookup");
    }

    @Test
    @DisplayName("Should track URL deleted metric")
    void shouldTrackUrlDeletedMetric() {
        // Given - Create and delete
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/delete-metric", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        restTemplate.exchange(
            baseUrl + "/api/urls/" + createResponse.code(),
            HttpMethod.DELETE,
            null,
            Void.class
        );

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/metrics/shortener.url.deleted",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("shortener.url.deleted");
    }

    // ==================== Prometheus Endpoint Tests ====================

    @Test
    @DisplayName("Should expose Prometheus metrics endpoint")
    void shouldExposePrometheusEndpoint() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            baseUrl + "/actuator/prometheus",
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("# HELP");
        assertThat(response.getBody()).contains("# TYPE");
    }

    // ==================== Environment Endpoint Tests ====================

    @Test
    @DisplayName("Should expose environment endpoint")
    void shouldExposeEnvironmentEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/env",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("propertySources");
    }

    // ==================== Loggers Endpoint Tests ====================

    @Test
    @DisplayName("Should expose loggers endpoint")
    void shouldExposeLoggersEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/loggers",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("levels");
        assertThat(response.getBody()).containsKey("loggers");
    }

    @Test
    @DisplayName("Should get specific logger level")
    void shouldGetSpecificLoggerLevel() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/loggers/com.urlshortener",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    // ==================== Beans Endpoint Tests ====================

    @Test
    @DisplayName("Should expose beans endpoint")
    void shouldExposeBeansEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/beans",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("contexts");
    }

    // ==================== Mappings Endpoint Tests ====================

    @Test
    @DisplayName("Should expose mappings endpoint")
    void shouldExposeMappingsEndpoint() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/mappings",
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("contexts");
    }
}
