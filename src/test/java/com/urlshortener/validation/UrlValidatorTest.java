package com.urlshortener.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive unit tests for UrlValidator.
 * Tests custom @ValidUrl annotation validation logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UrlValidator Unit Tests")
class UrlValidatorTest {

    private UrlValidator urlValidator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ValidUrl validUrlAnnotation;

    @BeforeEach
    void setUp() {
        urlValidator = new UrlValidator();

        // Mock the context builder for constraint violations
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
            org.mockito.Mockito.mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);

        lenient().when(context.buildConstraintViolationWithTemplate(anyString()))
            .thenReturn(builder);
        lenient().when(builder.addConstraintViolation())
            .thenReturn(context);
    }

    // ==================== Valid URL Tests ====================

    @Test
    @DisplayName("Should accept valid HTTP URL")
    void shouldAcceptValidHttpUrl() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String validHttpUrl = "http://www.example.com";

        // When
        boolean isValid = urlValidator.isValid(validHttpUrl, context);

        // Then
        assertTrue(isValid, "Valid HTTP URL should be accepted");
    }

    @Test
    @DisplayName("Should accept valid HTTPS URL")
    void shouldAcceptValidHttpsUrl() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String validHttpsUrl = "https://www.example.com";

        // When
        boolean isValid = urlValidator.isValid(validHttpsUrl, context);

        // Then
        assertTrue(isValid, "Valid HTTPS URL should be accepted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.example.com",
        "https://example.com",
        "https://example.com/path",
        "https://example.com/path/to/resource",
        "https://example.com/path?query=param",
        "https://example.com/path?query=param&other=value",
        "https://example.com/path#fragment",
        "https://example.com:8080/path",
        "https://subdomain.example.com",
        "https://sub.subdomain.example.com",
        "https://example.com/path/with-hyphens",
        "https://example.com/path_with_underscores",
        "https://example.com/path~with~tildes",
        "https://example.co.uk",
        "https://192.168.1.1",
        "https://192.168.1.1:8080/path",
        "http://localhost",
        "http://localhost:8080",
        "http://localhost:8080/api/test"
    })
    @DisplayName("Should accept various valid URL formats")
    void shouldAcceptVariousValidUrlFormats(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertTrue(isValid, "URL should be valid: " + url);
    }

    // ==================== Invalid URL Tests ====================

    @Test
    @DisplayName("Should reject invalid URL format")
    void shouldRejectInvalidUrl() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String invalidUrl = "not-a-valid-url";

        // When
        boolean isValid = urlValidator.isValid(invalidUrl, context);

        // Then
        assertFalse(isValid, "Invalid URL should be rejected");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject null or empty URL")
    void shouldRejectNullOrEmptyUrl(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "Null or empty URL should be rejected");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        " ",
        "   ",
        "\t",
        "\n"
    })
    @DisplayName("Should reject blank URLs")
    void shouldRejectBlankUrls(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "Blank URL should be rejected");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "htp://example.com",
        "htps://example.com",
        "ttp://example.com",
        "http//example.com",
        "http:/example.com",
        "http:example.com",
        "://example.com",
        "//example.com"
    })
    @DisplayName("Should reject malformed URLs")
    void shouldRejectMalformedUrls(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "Malformed URL should be rejected: " + url);
    }

    // ==================== Protocol Tests ====================

    @Test
    @DisplayName("Should reject unsupported protocol")
    void shouldRejectUnsupportedProtocol() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String ftpUrl = "ftp://example.com/file.txt";

        // When
        boolean isValid = urlValidator.isValid(ftpUrl, context);

        // Then
        assertFalse(isValid, "FTP protocol should be rejected when not in allowed list");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com",
        "file:///path/to/file",
        "mailto:user@example.com",
        "javascript:alert('xss')",
        "data:text/html,<script>alert('xss')</script>"
    })
    @DisplayName("Should reject various unsupported protocols")
    void shouldRejectVariousUnsupportedProtocols(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "Unsupported protocol should be rejected: " + url);
    }

    @Test
    @DisplayName("Should accept custom allowed protocol")
    void shouldAcceptCustomAllowedProtocol() {
        // Given
        initializeValidator(new String[]{"http", "https", "ftp"}, 2048);
        String ftpUrl = "ftp://example.com/file.txt";

        // When
        boolean isValid = urlValidator.isValid(ftpUrl, context);

        // Then
        assertTrue(isValid, "FTP should be accepted when explicitly allowed");
    }

    // ==================== Length Tests ====================

    @Test
    @DisplayName("Should reject URL exceeding max length")
    void shouldRejectUrlExceedingMaxLength() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 100);
        String longUrl = "https://www.example.com/" + "a".repeat(200);

        // When
        boolean isValid = urlValidator.isValid(longUrl, context);

        // Then
        assertFalse(isValid, "URL exceeding max length should be rejected");
    }

    @Test
    @DisplayName("Should accept URL at max length boundary")
    void shouldAcceptUrlAtMaxLengthBoundary() {
        // Given
        int maxLength = 100;
        initializeValidator(new String[]{"http", "https"}, maxLength);
        String url = "https://www.example.com/" + "a".repeat(maxLength - 27); // 27 = length of base URL

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertTrue(isValid, "URL at max length boundary should be accepted");
    }

    @Test
    @DisplayName("Should accept very long URL within limit")
    void shouldAcceptVeryLongUrlWithinLimit() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String longUrl = "https://www.example.com/" + "a".repeat(1500);

        // When
        boolean isValid = urlValidator.isValid(longUrl, context);

        // Then
        assertTrue(isValid, "Long URL within limit should be accepted");
    }

    // ==================== Special Characters and Encoding Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com/search?q=hello+world",
        "https://example.com/path%20with%20spaces",
        "https://example.com/path?q=%E4%BD%A0%E5%A5%BD", // URL encoded Chinese
        "https://example.com/file(1).html",
        "https://example.com/path/[bracket]",
        "https://example.com/path/{brace}",
        "https://example.com/api/v1.0",
        "https://example.com/path;param=value",
        "https://example.com/path,with,commas"
    })
    @DisplayName("Should handle URLs with special characters")
    void shouldHandleUrlsWithSpecialCharacters(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertTrue(isValid, "URL with special characters should be valid: " + url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com/path with spaces",
        "https://example.com/path\twith\ttabs",
        "https://example.com/path\nwith\nnewlines"
    })
    @DisplayName("Should reject URLs with unencoded whitespace")
    void shouldRejectUrlsWithUnencodedWhitespace(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "URL with unencoded whitespace should be rejected: " + url);
    }

    // ==================== Security Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert('xss')",
        "javascript:void(0)",
        "data:text/html,<script>alert('xss')</script>",
        "vbscript:msgbox('xss')"
    })
    @DisplayName("Should reject potentially dangerous URLs")
    void shouldRejectPotentiallyDangerousUrls(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "Potentially dangerous URL should be rejected: " + url);
    }

    // ==================== Domain and IP Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "https://192.168.1.1",
        "https://10.0.0.1",
        "https://172.16.0.1",
        "https://127.0.0.1",
        "http://localhost",
        "https://0.0.0.0"
    })
    @DisplayName("Should accept IP addresses and localhost")
    void shouldAcceptIpAddressesAndLocalhost(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertTrue(isValid, "IP address or localhost should be valid: " + url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com",
        "https://example.co.uk",
        "https://example.org",
        "https://sub.example.com",
        "https://a.b.c.example.com"
    })
    @DisplayName("Should accept various domain formats")
    void shouldAcceptVariousDomainFormats(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertTrue(isValid, "Domain format should be valid: " + url);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle URL with only protocol and domain")
    void shouldHandleUrlWithOnlyProtocolAndDomain() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String minimalUrl = "https://example.com";

        // When
        boolean isValid = urlValidator.isValid(minimalUrl, context);

        // Then
        assertTrue(isValid, "Minimal valid URL should be accepted");
    }

    @Test
    @DisplayName("Should handle URL with maximum valid complexity")
    void shouldHandleUrlWithMaximumComplexity() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String complexUrl = "https://user:pass@sub.example.com:8080/path/to/resource.html?query=param&other=value#fragment";

        // When
        boolean isValid = urlValidator.isValid(complexUrl, context);

        // Then
        assertTrue(isValid, "Complex URL should be accepted");
    }

    @Test
    @DisplayName("Should handle URL with international domain")
    void shouldHandleUrlWithInternationalDomain() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String idnUrl = "https://münchen.de"; // International domain name

        // When
        boolean isValid = urlValidator.isValid(idnUrl, context);

        // Then
        assertTrue(isValid, "International domain should be accepted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://",
        "http://",
        "https:///path",
        "http:///path"
    })
    @DisplayName("Should reject URLs without domain")
    void shouldRejectUrlsWithoutDomain(String url) {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);

        // When
        boolean isValid = urlValidator.isValid(url, context);

        // Then
        assertFalse(isValid, "URL without domain should be rejected: " + url);
    }

    @Test
    @DisplayName("Should handle trimmed URL")
    void shouldHandleTrimmedUrl() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 2048);
        String urlWithSpaces = "  https://www.example.com  ";

        // When
        boolean isValid = urlValidator.isValid(urlWithSpaces, context);

        // Then
        // Validator should trim the URL before validation
        assertTrue(isValid, "URL with leading/trailing spaces should be trimmed and accepted");
    }

    // ==================== Configuration Tests ====================

    @Test
    @DisplayName("Should respect custom protocol configuration")
    void shouldRespectCustomProtocolConfiguration() {
        // Given
        initializeValidator(new String[]{"https"}, 2048); // Only HTTPS allowed

        // When
        boolean isValidHttps = urlValidator.isValid("https://example.com", context);
        boolean isValidHttp = urlValidator.isValid("http://example.com", context);

        // Then
        assertTrue(isValidHttps, "HTTPS should be accepted");
        assertFalse(isValidHttp, "HTTP should be rejected when not in allowed protocols");
    }

    @Test
    @DisplayName("Should respect custom max length configuration")
    void shouldRespectCustomMaxLengthConfiguration() {
        // Given
        initializeValidator(new String[]{"http", "https"}, 50);

        // When
        boolean isValidShort = urlValidator.isValid("https://example.com", context);
        boolean isValidLong = urlValidator.isValid("https://example.com/" + "a".repeat(50), context);

        // Then
        assertTrue(isValidShort, "Short URL should be accepted");
        assertFalse(isValidLong, "Long URL exceeding custom limit should be rejected");
    }

    // ==================== Helper Methods ====================

    /**
     * Initializes the validator with custom configuration.
     *
     * @param protocols allowed protocols
     * @param maxLength maximum URL length
     */
    private void initializeValidator(String[] protocols, int maxLength) {
        ValidUrl annotation = new ValidUrl() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ValidUrl.class;
            }

            @Override
            public String message() {
                return "Invalid URL";
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public String[] protocols() {
                return protocols;
            }

            @Override
            public int maxLength() {
                return maxLength;
            }

            @Override
            public int minLength() {
                return 10;
            }
        };

        urlValidator.initialize(annotation);
    }
}
