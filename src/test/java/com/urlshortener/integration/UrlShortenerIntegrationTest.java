package com.urlshortener.integration;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlMetadataResponse;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the URL shortener using @SpringBootTest.
 * Tests the entire application stack including controllers, services, and database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("URL Shortener End-to-End Integration Tests")
class UrlShortenerIntegrationTest {

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

    // ==================== End-to-End Workflow Tests ====================

    @Test
    @DisplayName("Should create and resolve short URL end-to-end")
    void shouldCreateAndResolveShortUrl_EndToEnd() {
        // Given
        String longUrl = "https://www.example.com/test/page";
        CreateUrlRequest request = new CreateUrlRequest(longUrl, null);

        // Step 1: Create short URL via POST /api/urls
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UrlResponse> createResponse = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            UrlResponse.class
        );

        // Assert creation response
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getHeaders().getLocation()).isNotNull();

        UrlResponse urlResponse = createResponse.getBody();
        String code = urlResponse.code();

        assertThat(code).isNotNull().hasSize(7);
        assertThat(urlResponse.longUrl()).isEqualTo(longUrl);
        assertThat(urlResponse.shortUrl()).contains(code);
        assertThat(urlResponse.createdAt()).isNotNull();
        assertThat(urlResponse.expiresAt()).isNotNull();

        // Step 2: Verify in database
        Optional<UrlMapping> saved = repository.findByCode(code);
        assertThat(saved).isPresent();
        assertThat(saved.get().getLongUrl()).isEqualTo(longUrl);
        assertThat(saved.get().getHitCount()).isEqualTo(0L);

        // Step 3: Resolve short URL via GET /r/{code}
        ResponseEntity<Void> redirectResponse = noRedirectRestTemplate.exchange(
            baseUrl + "/r/" + code,
            HttpMethod.GET,
            null,
            Void.class
        );

        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation())
            .isNotNull()
            .hasToString(longUrl);

        // Step 4: Check metadata via GET /api/urls/{code}
        ResponseEntity<UrlMetadataResponse> metadataResponse = restTemplate.getForEntity(
            baseUrl + "/api/urls/" + code,
            UrlMetadataResponse.class
        );

        assertThat(metadataResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metadataResponse.getBody()).isNotNull();
        assertThat(metadataResponse.getBody().hitCount()).isEqualTo(1L);
        assertThat(metadataResponse.getBody().code()).isEqualTo(code);
        assertThat(metadataResponse.getBody().longUrl()).isEqualTo(longUrl);

        // Step 5: Redirect again and verify hit count increases
        noRedirectRestTemplate.exchange(
            baseUrl + "/r/" + code,
            HttpMethod.GET,
            null,
            Void.class
        );

        metadataResponse = restTemplate.getForEntity(
            baseUrl + "/api/urls/" + code,
            UrlMetadataResponse.class
        );

        assertThat(metadataResponse.getBody().hitCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should return same code when same URL submitted twice (idempotent)")
    void shouldReturnSameCode_WhenSameUrlSubmittedTwice() {
        // Given
        String longUrl = "https://www.example.com/idempotent-test";
        CreateUrlRequest request = new CreateUrlRequest(longUrl, null);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request);

        // When - Submit same URL twice
        ResponseEntity<UrlResponse> firstResponse = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            UrlResponse.class
        );

        ResponseEntity<UrlResponse> secondResponse = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            entity,
            UrlResponse.class
        );

        // Then - Should return same code
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().code())
            .isEqualTo(secondResponse.getBody().code());

        // Verify only one entry in database
        List<UrlMapping> allMappings = repository.findAll();
        assertThat(allMappings).hasSize(1);
        assertThat(allMappings.get(0).getLongUrl()).isEqualTo(longUrl);
    }

    @Test
    @DisplayName("Should create different codes for different URLs")
    void shouldCreateDifferentCodes_ForDifferentUrls() {
        // Given
        CreateUrlRequest request1 = new CreateUrlRequest("https://www.example.com/page1", null);
        CreateUrlRequest request2 = new CreateUrlRequest("https://www.example.com/page2", null);
        CreateUrlRequest request3 = new CreateUrlRequest("https://www.example.com/page3", null);

        // When
        UrlResponse response1 = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request1,
            UrlResponse.class
        );
        UrlResponse response2 = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request2,
            UrlResponse.class
        );
        UrlResponse response3 = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request3,
            UrlResponse.class
        );

        // Then
        assertThat(response1.code()).isNotEqualTo(response2.code());
        assertThat(response2.code()).isNotEqualTo(response3.code());
        assertThat(response1.code()).isNotEqualTo(response3.code());

        // Verify three entries in database
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should complete full lifecycle: create, redirect, update, delete")
    void shouldCompleteFullLifecycle() {
        // Step 1: Create URL
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/lifecycle", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        String code = createResponse.code();
        assertThat(repository.existsByCode(code)).isTrue();

        // Step 2: Redirect
        ResponseEntity<Void> redirectResponse = noRedirectRestTemplate.exchange(
            baseUrl + "/r/" + code,
            HttpMethod.GET,
            null,
            Void.class
        );
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        // Step 3: Update expiration
        Instant newExpiration = Instant.now().plus(60, ChronoUnit.DAYS);
        ResponseEntity<Void> updateResponse = restTemplate.exchange(
            baseUrl + "/api/urls/" + code + "/expiration?expiresAt=" + newExpiration,
            HttpMethod.PATCH,
            null,
            Void.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Step 4: Delete URL
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            baseUrl + "/api/urls/" + code,
            HttpMethod.DELETE,
            null,
            Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(repository.existsByCode(code)).isFalse();

        // Step 5: Verify redirect returns 404
        ResponseEntity<String> notFoundResponse = noRedirectRestTemplate.exchange(
            baseUrl + "/r/" + code,
            HttpMethod.GET,
            null,
            String.class
        );
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== Concurrent Access Tests ====================

    @Test
    @DisplayName("Should handle multiple concurrent create requests without collisions")
    void shouldHandleMultipleConcurrentRequests() throws Exception {
        // Given
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<UrlResponse>> futures = new ArrayList<>();

        // When - Submit URLs concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            Future<UrlResponse> future = executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // Wait for all threads to be ready

                    CreateUrlRequest request = new CreateUrlRequest(
                        "https://example.com/concurrent/test" + index,
                        null
                    );

                    return restTemplate.postForObject(
                        baseUrl + "/api/urls",
                        request,
                        UrlResponse.class
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Then - All requests should succeed with unique codes
        List<String> codes = new ArrayList<>();
        for (Future<UrlResponse> future : futures) {
            UrlResponse response = future.get(10, TimeUnit.SECONDS);
            assertThat(response).isNotNull();
            assertThat(response.code()).isNotNull();
            codes.add(response.code());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify all codes are unique
        assertThat(codes).hasSize(threadCount);
        assertThat(codes).doesNotHaveDuplicates();

        // Verify all entries in database
        assertThat(repository.count()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("Should handle concurrent redirects to same URL")
    void shouldHandleConcurrentRedirects() throws Exception {
        // Given - Create a URL first
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/popular", null);
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );
        String code = createResponse.code();

        // When - Multiple concurrent redirects
        int redirectCount = 20; // Reduced for faster testing
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < redirectCount; i++) {
            Future<Integer> future = executor.submit(() -> {
                ResponseEntity<Void> response = noRedirectRestTemplate.exchange(
                    baseUrl + "/r/" + code,
                    HttpMethod.GET,
                    null,
                    Void.class
                );
                return response.getStatusCode().value();
            });
            futures.add(future);
        }

        // Then - All redirects should succeed
        for (Future<Integer> future : futures) {
            Integer statusCode = future.get(30, TimeUnit.SECONDS);
            assertThat(statusCode).isEqualTo(302);
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify hit count was incremented correctly
        UrlMetadataResponse metadata = restTemplate.getForObject(
            baseUrl + "/api/urls/" + code,
            UrlMetadataResponse.class
        );

        assertThat(metadata.hitCount()).isEqualTo((long) redirectCount);
    }

    // ==================== Custom Expiration Tests ====================

    @Test
    @DisplayName("Should create URL with custom expiration")
    void shouldCreateUrlWithCustomExpiration() {
        // Given
        Instant customExpiration = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateUrlRequest request = new CreateUrlRequest(
            "https://example.com/custom-expiry",
            customExpiration
        );

        // When
        UrlResponse response = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        // Then
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.expiresAt()).isCloseTo(customExpiration, org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));

        // Verify in database
        Optional<UrlMapping> mapping = repository.findByCode(response.code());
        assertThat(mapping).isPresent();
        assertThat(mapping.get().getExpiresAt())
            .isCloseTo(customExpiration, org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Should update expiration to null (permanent)")
    void shouldUpdateExpirationToNull() {
        // Given - Create URL with expiration
        CreateUrlRequest request = new CreateUrlRequest(
            "https://example.com/make-permanent",
            Instant.now().plus(30, ChronoUnit.DAYS)
        );
        UrlResponse createResponse = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );
        String code = createResponse.code();

        // When - Update to null (permanent)
        ResponseEntity<Void> updateResponse = restTemplate.exchange(
            baseUrl + "/api/urls/" + code + "/expiration",
            HttpMethod.PATCH,
            null,
            Void.class
        );

        // Then
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify in database
        Optional<UrlMapping> mapping = repository.findByCode(code);
        assertThat(mapping).isPresent();
        assertThat(mapping.get().getExpiresAt()).isNull();
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should return 400 for invalid URL")
    void shouldReturn400_ForInvalidUrl() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest("not-a-valid-url", null);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/urls",
            request,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("title");
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return 404 for non-existent code")
    void shouldReturn404_ForNonExistentCode() {
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/r/nonexist",
            HttpMethod.GET,
            null,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("not found");
    }

    @Test
    @DisplayName("Should return 404 when getting metadata for non-existent code")
    void shouldReturn404_WhenGettingNonExistentMetadata() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            baseUrl + "/api/urls/nonexist",
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent code")
    void shouldReturn404_WhenDeletingNonExistentCode() {
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/urls/nonexist",
            HttpMethod.DELETE,
            null,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== URL Encoding and Special Characters Tests ====================

    @Test
    @DisplayName("Should handle URLs with query parameters")
    void shouldHandleUrlsWithQueryParameters() {
        // Given
        String urlWithParams = "https://example.com/search?q=test&category=all&page=1";
        CreateUrlRequest request = new CreateUrlRequest(urlWithParams, null);

        // When
        UrlResponse response = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        // Then
        assertThat(response.longUrl()).isEqualTo(urlWithParams);

        // Verify redirect works
        ResponseEntity<Void> redirectResponse = noRedirectRestTemplate.exchange(
            baseUrl + "/r/" + response.code(),
            HttpMethod.GET,
            null,
            Void.class
        );

        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation().toString())
            .isEqualTo(urlWithParams);
    }

    @Test
    @DisplayName("Should handle URLs with fragments")
    void shouldHandleUrlsWithFragments() {
        // Given
        String urlWithFragment = "https://example.com/docs#section-3";
        CreateUrlRequest request = new CreateUrlRequest(urlWithFragment, null);

        // When
        UrlResponse response = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        // Then
        assertThat(response.longUrl()).isEqualTo(urlWithFragment);

        // Verify redirect works
        ResponseEntity<Void> redirectResponse = noRedirectRestTemplate.exchange(
            baseUrl + "/r/" + response.code(),
            HttpMethod.GET,
            null,
            Void.class
        );

        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation().toString())
            .isEqualTo(urlWithFragment);
    }

    @Test
    @DisplayName("Should handle URLs with encoded characters")
    void shouldHandleUrlsWithEncodedCharacters() {
        // Given
        String urlWithEncoding = "https://example.com/search?q=hello%20world&emoji=%F0%9F%98%80";
        CreateUrlRequest request = new CreateUrlRequest(urlWithEncoding, null);

        // When
        UrlResponse response = restTemplate.postForObject(
            baseUrl + "/api/urls",
            request,
            UrlResponse.class
        );

        // Then
        assertThat(response.longUrl()).isEqualTo(urlWithEncoding);
    }

}
