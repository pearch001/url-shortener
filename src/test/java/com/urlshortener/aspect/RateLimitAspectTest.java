package com.urlshortener.aspect;

import com.urlshortener.annotation.RateLimited;
import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link RateLimitAspect}.
 * <p>
 * Tests the rate limiting logic, IP extraction, and bucket management.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RateLimited rateLimitedAnnotation;

    @Mock
    private HttpServletRequest httpServletRequest;

    private RateLimitAspect rateLimitAspect;

    @BeforeEach
    void setUp() {
        rateLimitAspect = new RateLimitAspect(rateLimitProperties);

        // Default configuration (lenient to avoid UnnecessaryStubbingException)
        lenient().when(rateLimitProperties.isEnabled()).thenReturn(true);
        lenient().when(rateLimitProperties.getCapacity()).thenReturn(10);
        lenient().when(rateLimitProperties.getRefillTokens()).thenReturn(10L);
        lenient().when(rateLimitProperties.getRefillPeriodMinutes()).thenReturn(1L);

        // Default annotation values (lenient to avoid UnnecessaryStubbingException)
        lenient().when(rateLimitedAnnotation.capacity()).thenReturn(10);
        lenient().when(rateLimitedAnnotation.refillTokens()).thenReturn(10L);
        lenient().when(rateLimitedAnnotation.refillPeriodMinutes()).thenReturn(1L);

        // Clear cache before each test
        rateLimitAspect.clearCache();
    }

    @Test
    void shouldAllowRequestWhenBelowRateLimit() throws Throwable {
        // Arrange
        String clientIp = "192.168.1.100";
        setupRequestContext(clientIp);
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        Object result = rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        // Assert
        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    void shouldBlockRequestWhenRateLimitExceeded() throws Throwable {
        // Arrange
        String clientIp = "192.168.1.100";
        setupRequestContext(clientIp);
        when(joinPoint.proceed()).thenReturn("success");

        // Act - Consume all tokens
        for (int i = 0; i < 10; i++) {
            rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);
        }

        // Assert - 11th request should be blocked
        assertThatThrownBy(() -> rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation))
            .isInstanceOf(RateLimitExceededException.class)
            .hasMessageContaining("Too many requests");
    }

    @Test
    void shouldTrackRateLimitsPerIpAddress() throws Throwable {
        // Arrange
        String clientIp1 = "192.168.1.100";
        String clientIp2 = "192.168.1.101";
        when(joinPoint.proceed()).thenReturn("success");

        // Act - Exhaust limit for IP1
        setupRequestContext(clientIp1);
        for (int i = 0; i < 10; i++) {
            rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);
        }

        // Assert - IP1 is blocked
        assertThatThrownBy(() -> rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation))
            .isInstanceOf(RateLimitExceededException.class);

        // Assert - IP2 is still allowed
        setupRequestContext(clientIp2);
        Object result = rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);
        assertThat(result).isEqualTo("success");
    }

    @Test
    void shouldBypassRateLimitWhenDisabled() throws Throwable {
        // Arrange
        when(rateLimitProperties.isEnabled()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("success");
        String clientIp = "192.168.1.100";
        setupRequestContext(clientIp);

        // Act - Try to exceed limit
        for (int i = 0; i < 15; i++) {
            Object result = rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);
            assertThat(result).isEqualTo("success");
        }

        // Assert - All requests should succeed when disabled
        verify(joinPoint, times(15)).proceed();
    }

    @Test
    void shouldExtractIpFromXForwardedForHeader() throws Throwable {
        // Arrange
        lenient().when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 192.168.1.1");
        lenient().when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        lenient().when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        setupRequestAttributes();
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        // Assert - Should use the first IP from X-Forwarded-For
        assertThat(rateLimitAspect.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldExtractIpFromXRealIpHeader() throws Throwable {
        // Arrange
        lenient().when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("203.0.113.2");
        lenient().when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        setupRequestAttributes();
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        // Assert
        assertThat(rateLimitAspect.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldExtractIpFromRemoteAddr() throws Throwable {
        // Arrange
        lenient().when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        lenient().when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        setupRequestAttributes();
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        // Assert
        assertThat(rateLimitAspect.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldIncludeClientIpInException() throws Throwable {
        // Arrange
        String clientIp = "192.168.1.100";
        setupRequestContext(clientIp);
        when(joinPoint.proceed()).thenReturn("success");

        // Exhaust tokens
        for (int i = 0; i < 10; i++) {
            rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);
        }

        // Act & Assert
        assertThatThrownBy(() -> rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation))
            .isInstanceOf(RateLimitExceededException.class)
            .extracting(e -> ((RateLimitExceededException) e).getClientIdentifier())
            .isEqualTo(clientIp);
    }

    @Test
    void shouldClearCache() throws Throwable {
        // Arrange
        setupRequestContext("192.168.1.100");
        when(joinPoint.proceed()).thenReturn("success");
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        assertThat(rateLimitAspect.getCacheSize()).isEqualTo(1);

        // Act
        rateLimitAspect.clearCache();

        // Assert
        assertThat(rateLimitAspect.getCacheSize()).isZero();
    }

    @Test
    void shouldCreateSeparateBucketsForDifferentIps() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        setupRequestContext("192.168.1.100");
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        setupRequestContext("192.168.1.101");
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        setupRequestContext("192.168.1.102");
        rateLimitAspect.rateLimit(joinPoint, rateLimitedAnnotation);

        // Assert
        assertThat(rateLimitAspect.getCacheSize()).isEqualTo(3);
    }

    /**
     * Helper method to set up request context with a specific IP.
     */
    private void setupRequestContext(String clientIp) {
        lenient().when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        lenient().when(httpServletRequest.getRemoteAddr()).thenReturn(clientIp);
        setupRequestAttributes();
    }

    /**
     * Helper method to set up request attributes.
     */
    private void setupRequestAttributes() {
        ServletRequestAttributes attributes = new ServletRequestAttributes(httpServletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
    }
}
