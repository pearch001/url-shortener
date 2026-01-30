package com.urlshortener.config;

import com.urlshortener.ratelimit.RateLimitInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for the URL Shortener application.
 * Configures interceptors, CORS, and other web-related settings.
 *
 * <p><b>Configured Features:</b></p>
 * <ul>
 *   <li>Rate limiting interceptor (Bucket4j)</li>
 *   <li>CORS configuration for cross-origin requests</li>
 *   <li>Custom error handling (via GlobalExceptionHandler)</li>
 * </ul>
 *
 * <p><b>Rate Limiting:</b></p>
 * The rate limit interceptor is conditionally enabled based on configuration:
 * <pre>
 * url-shortener:
 *   rate-limit:
 *     enabled: true
 * </pre>
 *
 * <p><b>CORS:</b></p>
 * Configured to allow cross-origin requests from any origin in development.
 * For production, restrict to specific domains.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * Constructor with optional rate limit interceptor injection.
     * The interceptor is only available when urlshortener.rate-limit.enabled=true.
     *
     * @param rateLimitInterceptor the rate limit interceptor (optional)
     */
    @Autowired
    public WebMvcConfig(@Autowired(required = false) RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    /**
     * Registers interceptors for the application.
     * Rate limiting uses interceptor pattern which works reliably with HTTP requests.
     * (AOP aspects don't intercept controller methods called via DispatcherServlet)
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitInterceptor != null) {
            log.info("Registering rate limit interceptor");

            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**")  // Apply to all API endpoints
                    .excludePathPatterns(
                            "/actuator/**",      // Don't rate limit health checks
                            "/swagger-ui/**",    // Don't rate limit docs
                            "/api-docs/**"       // Don't rate limit OpenAPI
                    );

            log.info("Rate limit interceptor registered for /api/** paths");
        } else {
            log.info("Rate limiting is disabled, skipping interceptor registration");
        }
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) settings.
     * Allows cross-origin requests for the API.
     *
     * <p><b>Development Configuration:</b></p>
     * Allows all origins, methods, and headers for ease of development.
     *
     * <p><b>Production Configuration:</b></p>
     * Should be restricted to specific allowed origins:
     * <pre>
     * registry.addMapping("/api/**")
     *     .allowedOrigins("https://yourdomain.com")
     *     .allowedMethods("GET", "POST", "DELETE", "PATCH")
     *     .allowCredentials(true);
     * </pre>
     *
     * @param registry the CORS registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("Configuring CORS settings");

        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")  // Allow all origins (dev mode)
                .allowedMethods("GET", "POST", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Reset",
                        "Retry-After"
                )
                .allowCredentials(true)
                .maxAge(3600);  // Cache preflight response for 1 hour

        // Allow CORS for redirect endpoint (read-only)
        registry.addMapping("/r/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        log.info("CORS configured for /api/** and /r/** paths");
    }
}
