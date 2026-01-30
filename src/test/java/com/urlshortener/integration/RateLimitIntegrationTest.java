package com.urlshortener.integration;

import com.urlshortener.ratelimit.RateLimitInterceptor;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for rate limiting functionality.
 * <p>
 * Tests the complete rate limiting flow including:
 * - Request interception by RateLimitInterceptor
 * - IP address extraction
 * - Bucket token consumption
 * - 429 response with Retry-After header
 * </p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "urlshortener.rate-limit.enabled=true"
    })
@ActiveProfiles("test")
@DisplayName("Rate Limiting Integration Tests")
class RateLimitIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UrlMappingRepository repository;

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        repository.deleteAll();
        // Clear rate limit cache before each test
        rateLimitInterceptor.clearBuckets();
    }

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void shouldReturn429_WhenRateLimitExceeded() {
        // Arrange
        String clientIp = "192.168.1.100";
        CreateUrlRequest request = new CreateUrlRequest(
            "https://example.com/test",
            null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);

        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, headers);

        // Act - Make 10 successful requests (within limit)
        for (int i = 0; i < 10; i++) {
            // Use unique URL for each request to avoid caching/deduplication
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/test-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> uniqueEntity = new HttpEntity<>(uniqueRequest, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/urls",
                uniqueEntity,
                String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        // 11th request should be rate limited
        CreateUrlRequest exceededRequest = new CreateUrlRequest(
            "https://example.com/test-exceeded",
            null
        );
        HttpEntity<CreateUrlRequest> exceededEntity = new HttpEntity<>(exceededRequest, headers);

        ResponseEntity<String> rateLimitedResponse = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            exceededEntity,
            String.class
        );

        // Assert
        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimitedResponse.getHeaders().get("Retry-After")).isNotNull();
        assertThat(rateLimitedResponse.getBody()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Should track rate limits per IP address")
    void shouldTrackRateLimitsPerIpAddress() {
        // Arrange
        String clientIp1 = "192.168.1.100";
        String clientIp2 = "192.168.1.101";

        CreateUrlRequest request = new CreateUrlRequest(
            "https://example.com/test",
            null
        );

        // Act - Exhaust limit for IP1
        HttpHeaders headers1 = new HttpHeaders();
        headers1.setContentType(MediaType.APPLICATION_JSON);
        headers1.set("X-Forwarded-For", clientIp1);

        for (int i = 0; i < 10; i++) {
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/ip1-test-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(uniqueRequest, headers1);
            restTemplate.postForEntity(baseUrl + "/api/urls", entity, String.class);
        }

        // 11th request from IP1 should be blocked
        HttpEntity<CreateUrlRequest> entity1 = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/ip1-blocked", null),
            headers1
        );
        ResponseEntity<String> response1 = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity1,
            String.class
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Request from IP2 should still succeed
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.APPLICATION_JSON);
        headers2.set("X-Forwarded-For", clientIp2);

        HttpEntity<CreateUrlRequest> entity2 = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/ip2-allowed", null),
            headers2
        );
        ResponseEntity<String> response2 = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity2,
            String.class
        );

        // Assert
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should include Retry-After header in 429 response")
    void shouldIncludeRetryAfterHeader() {
        // Arrange
        String clientIp = "192.168.1.100";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);

        // Exhaust rate limit
        for (int i = 0; i < 10; i++) {
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/test-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(uniqueRequest, headers);
            restTemplate.postForEntity(baseUrl + "/api/urls", entity, String.class);
        }

        // Act - Make request that exceeds limit
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/test-exceeded", null),
            headers
        );
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().containsKey("Retry-After")).isTrue();

        String retryAfter = response.getHeaders().getFirst("Retry-After");
        assertThat(retryAfter).isNotNull();

        // Retry-After should be approximately 60 seconds (1 minute refill period)
        // Allow for 1-2 seconds of test execution time
        int retrySeconds = Integer.parseInt(retryAfter);
        assertThat(retrySeconds).isBetween(58, 60);
    }

    @Test
    @DisplayName("Should extract IP from X-Forwarded-For header")
    void shouldExtractIpFromXForwardedForHeader() {
        // Arrange
        String clientIp = "203.0.113.1";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Simulate proxy chain
        headers.set("X-Forwarded-For", clientIp + ", 192.168.1.1, 10.0.0.1");

        // Act - Exhaust limit using X-Forwarded-For
        for (int i = 0; i < 10; i++) {
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/forwarded-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(uniqueRequest, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/urls",
                entity,
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        // 11th request should be blocked
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/forwarded-blocked", null),
            headers
        );
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Should extract IP from X-Real-IP header")
    void shouldExtractIpFromXRealIpHeader() {
        // Arrange
        String clientIp = "203.0.113.2";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Real-IP", clientIp);

        // Act - Exhaust limit using X-Real-IP
        for (int i = 0; i < 10; i++) {
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/realip-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(uniqueRequest, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/urls",
                entity,
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        // 11th request should be blocked
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/realip-blocked", null),
            headers
        );
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Should return RFC 7807 problem detail for rate limit exceeded")
    void shouldReturnRfc7807ProblemDetail() {
        // Arrange
        String clientIp = "192.168.1.100";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);

        // Exhaust rate limit
        for (int i = 0; i < 10; i++) {
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/rfc7807-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(uniqueRequest, headers);
            restTemplate.postForEntity(baseUrl + "/api/urls", entity, String.class);
        }

        // Act - Exceed limit
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/rfc7807-exceeded", null),
            headers
        );
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();

        // Verify RFC 7807 fields are present
        String body = response.getBody();
        assertThat(body).contains("\"type\"");
        assertThat(body).contains("\"title\"");
        assertThat(body).contains("\"status\":429");
        assertThat(body).contains("\"detail\"");
        assertThat(body).contains("rate-limit-exceeded");
    }

    @Test
    @DisplayName("Should only rate limit POST endpoint, not GET")
    void shouldOnlyRateLimitPostEndpoint() {
        // Arrange - First create a URL
        String clientIp = "192.168.1.100";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);

        CreateUrlRequest createRequest = new CreateUrlRequest(
            "https://example.com/get-test",
            null
        );
        HttpEntity<CreateUrlRequest> createEntity = new HttpEntity<>(createRequest, headers);

        ResponseEntity<String> createResponse = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            createEntity,
            String.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Extract the code from response (simple JSON parsing)
        String code = createResponse.getBody()
            .split("\"code\":\"")[1]
            .split("\"")[0];

        // Exhaust POST rate limit
        for (int i = 1; i < 10; i++) {
            CreateUrlRequest uniqueRequest = new CreateUrlRequest(
                "https://example.com/get-test-" + i,
                null
            );
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(uniqueRequest, headers);
            restTemplate.postForEntity(baseUrl + "/api/urls", entity, String.class);
        }

        // Verify POST is rate limited
        HttpEntity<CreateUrlRequest> exceededEntity = new HttpEntity<>(
            new CreateUrlRequest("https://example.com/get-test-exceeded", null),
            headers
        );
        ResponseEntity<String> rateLimitedResponse = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            exceededEntity,
            String.class
        );
        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Act - Make multiple GET requests (should not be rate limited)
        for (int i = 0; i < 20; i++) {
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            ResponseEntity<String> getResponse = restTemplate.exchange(
                baseUrl + "/api/urls/" + code,
                HttpMethod.GET,
                getEntity,
                String.class
            );

            // Assert - All GET requests should succeed
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
