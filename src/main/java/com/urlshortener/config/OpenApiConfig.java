package com.urlshortener.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for the URL Shortener API.
 * Provides comprehensive API documentation accessible via Swagger UI.
 *
 * <p><b>Swagger UI:</b> http://localhost:8080/swagger-ui.html</p>
 * <p><b>OpenAPI JSON:</b> http://localhost:8080/api-docs</p>
 *
 * <p>This configuration provides:
 * <ul>
 *   <li>API metadata (title, version, description)</li>
 *   <li>Contact information</li>
 *   <li>License details</li>
 *   <li>Server configurations</li>
 *   <li>Common response schemas</li>
 *   <li>Example requests and responses</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${urlshortener.base-url}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(buildApiInfo())
                .servers(buildServers())
                .components(buildComponents())
                .externalDocs(buildExternalDocs());
    }

    /**
     * Builds API information metadata.
     */
    private Info buildApiInfo() {
        return new Info()
                .title("URL Shortener API")
                .version("1.0.0")
                .description("""
                    # URL Shortener REST API

                    A production-ready REST API for URL shortening service built with Spring Boot 3.x and Java 25.

                    ## Features
                    - Create shortened URLs with collision-safe code generation
                    - Automatic expiration and cleanup
                    - Access tracking and analytics
                    - Rate limiting to prevent abuse
                    - RFC 7807 compliant error responses
                    - Comprehensive monitoring and metrics

                    ## Base URL
                    All API requests should be made to: `%s`

                    ## Rate Limiting
                    - POST /api/urls: 10 requests per minute per IP
                    - Rate limit headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
                    - Exceeded limits return 429 Too Many Requests

                    ## Error Handling
                    All errors follow RFC 7807 Problem Details format with:
                    - type: URI identifying the error type
                    - title: Human-readable summary
                    - status: HTTP status code
                    - detail: Detailed error message
                    - instance: Request URI

                    ## Metrics
                    Prometheus metrics available at: /actuator/prometheus
                    """.formatted(baseUrl))
                .contact(new Contact()
                        .name("URL Shortener Team")
                        .email("support@urlshortener.com")
                        .url("https://github.com/urlshortener/api"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * Builds server configurations.
     */
    private List<Server> buildServers() {
        return List.of(
                new Server()
                        .url(baseUrl)
                        .description("Local development server"),
                new Server()
                        .url("https://api.urlshortener.com")
                        .description("Production server (example)")
        );
    }

    /**
     * Builds reusable components (schemas, responses, security schemes).
     */
    private Components buildComponents() {
        return new Components()
                .addSecuritySchemes("basicAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("Basic authentication for admin endpoints"))
                .addSecuritySchemes("apiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("API key for authenticated requests"))
                .addResponses("BadRequest", buildBadRequestResponse())
                .addResponses("NotFound", buildNotFoundResponse())
                .addResponses("Gone", buildGoneResponse())
                .addResponses("TooManyRequests", buildTooManyRequestsResponse())
                .addResponses("InternalServerError", buildInternalServerErrorResponse());
    }

    /**
     * Builds external documentation link.
     */
    private ExternalDocumentation buildExternalDocs() {
        return new ExternalDocumentation()
                .description("URL Shortener Documentation")
                .url("https://github.com/urlshortener/api/wiki");
    }

    /**
     * Creates OpenAPI customizer to add global tags.
     */
    @Bean
    public OpenApiCustomizer globalTagsCustomizer() {
        return openApi -> openApi.setTags(List.of(
                new Tag()
                        .name("URL Management")
                        .description("Endpoints for creating and managing short URLs"),
                new Tag()
                        .name("URL Redirection")
                        .description("Endpoints for redirecting short URLs to original destinations"),
                new Tag()
                        .name("URL Shortener")
                        .description("Legacy API for URL shortening operations"),
                new Tag()
                        .name("Health")
                        .description("Health check and monitoring endpoints")
        ));
    }

    /**
     * Grouped API for public endpoints (URL management and redirection).
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-api")
                .displayName("Public API")
                .pathsToMatch("/api/**", "/r/**")
                .build();
    }

    /**
     * Grouped API for actuator endpoints (monitoring and health checks).
     */
    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("actuator")
                .displayName("Actuator")
                .pathsToMatch("/actuator/**")
                .build();
    }

    /**
     * Common 400 Bad Request response.
     */
    private ApiResponse buildBadRequestResponse() {
        return new ApiResponse()
                .description("Invalid request - validation failed")
                .content(new Content()
                        .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }

    /**
     * Common 404 Not Found response.
     */
    private ApiResponse buildNotFoundResponse() {
        return new ApiResponse()
                .description("Resource not found")
                .content(new Content()
                        .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }

    /**
     * Common 410 Gone response.
     */
    private ApiResponse buildGoneResponse() {
        return new ApiResponse()
                .description("Resource has expired and is no longer available")
                .content(new Content()
                        .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }

    /**
     * Common 429 Too Many Requests response.
     */
    private ApiResponse buildTooManyRequestsResponse() {
        return new ApiResponse()
                .description("Rate limit exceeded - too many requests")
                .content(new Content()
                        .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }

    /**
     * Common 500 Internal Server Error response.
     */
    private ApiResponse buildInternalServerErrorResponse() {
        return new ApiResponse()
                .description("Internal server error")
                .content(new Content()
                        .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }
}
