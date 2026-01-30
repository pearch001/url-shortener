package com.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for URL Shortener service.
 *
 * <p>This application provides a REST API for URL shortening with features:
 * <ul>
 *   <li>URL shortening with collision-safe code generation</li>
 *   <li>Custom short codes (aliases)</li>
 *   <li>URL expiration with automatic cleanup</li>
 *   <li>Access tracking and analytics</li>
 *   <li>Rate limiting</li>
 *   <li>OpenAPI/Swagger documentation</li>
 * </ul>
 *
 * <p><b>Enabled Features:</b></p>
 * <ul>
 *   <li>@EnableScheduling - For scheduled cleanup tasks</li>
 * </ul>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
