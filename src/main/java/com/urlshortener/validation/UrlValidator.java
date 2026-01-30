package com.urlshortener.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validator implementation for {@link ValidUrl} annotation.
 * Performs comprehensive URL validation including format, protocol, and length checks.
 *
 * <p>Validation Steps:
 * <ol>
 *   <li>Null and blank checks</li>
 *   <li>Length validation (min/max)</li>
 *   <li>URL format validation using {@link URL} class</li>
 *   <li>Protocol validation (HTTP/HTTPS)</li>
 *   <li>Host presence validation</li>
 * </ol>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class UrlValidator implements ConstraintValidator<ValidUrl, String> {

    private Set<String> allowedProtocols;
    private int maxLength;
    private int minLength;

    /**
     * Initializes the validator with parameters from the annotation.
     *
     * @param constraintAnnotation the annotation instance
     */
    @Override
    public void initialize(ValidUrl constraintAnnotation) {
        this.allowedProtocols = new HashSet<>(Arrays.asList(constraintAnnotation.protocols()));
        this.maxLength = constraintAnnotation.maxLength();
        this.minLength = constraintAnnotation.minLength();
    }

    /**
     * Validates the URL string against all configured constraints.
     *
     * @param urlString the URL string to validate
     * @param context   the constraint validator context
     * @return true if the URL is valid, false otherwise
     */
    @Override
    public boolean isValid(String urlString, ConstraintValidatorContext context) {
        // Null or blank URLs are invalid
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        // Trim the URL
        urlString = urlString.trim();

        // Disable default constraint violation
        context.disableDefaultConstraintViolation();

        // Validate URL length
        if (!validateLength(urlString, context)) {
            return false;
        }

        // Check for unencoded whitespace
        if (containsUnencodedWhitespace(urlString)) {
            addConstraintViolation(context,
                "URL contains unencoded whitespace characters");
            return false;
        }

        // Validate URL format and structure
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            log.debug("Invalid URL format: {}", urlString, e);
            addConstraintViolation(context,
                "Invalid URL format: " + e.getMessage());
            return false;
        }

        // Validate protocol
        if (!validateProtocol(url, context)) {
            return false;
        }

        // Validate host presence
        if (!validateHost(url, context)) {
            return false;
        }

        // Additional security checks
        if (!validateSecurity(url, context)) {
            return false;
        }

        return true;
    }

    /**
     * Validates URL length constraints.
     */
    private boolean validateLength(String urlString, ConstraintValidatorContext context) {
        int length = urlString.length();

        if (length < minLength) {
            addConstraintViolation(context,
                String.format("URL is too short. Minimum length is %d characters", minLength));
            return false;
        }

        if (length > maxLength) {
            addConstraintViolation(context,
                String.format("URL exceeds maximum length of %d characters", maxLength));
            return false;
        }

        return true;
    }

    /**
     * Validates URL protocol against allowed protocols.
     */
    private boolean validateProtocol(URL url, ConstraintValidatorContext context) {
        String protocol = url.getProtocol().toLowerCase();

        if (!allowedProtocols.contains(protocol)) {
            addConstraintViolation(context,
                String.format("Protocol '%s' is not allowed. Allowed protocols: %s",
                    protocol, allowedProtocols));
            return false;
        }

        return true;
    }

    /**
     * Validates that the URL has a valid host component.
     */
    private boolean validateHost(URL url, ConstraintValidatorContext context) {
        String host = url.getHost();

        if (host == null || host.isBlank()) {
            addConstraintViolation(context, "URL must contain a valid host");
            return false;
        }

        // Check for localhost/loopback in production scenarios if needed
        if (isLocalhost(host)) {
            log.debug("URL points to localhost: {}", host);
            // Allow localhost for development, but log it
        }

        return true;
    }

    /**
     * Performs additional security validations.
     */
    private boolean validateSecurity(URL url, ConstraintValidatorContext context) {
        String urlString = url.toString();

        // Check for potentially dangerous patterns
        if (containsDangerousPatterns(urlString)) {
            addConstraintViolation(context, "URL contains potentially dangerous patterns");
            return false;
        }

        // Check for excessive nesting or suspicious characters
        if (urlString.contains("..") && !urlString.contains("...")) {
            // Allow ellipsis but flag directory traversal attempts
            log.warn("URL contains directory traversal pattern: {}", urlString);
        }

        return true;
    }

    /**
     * Checks if the host is localhost or a loopback address.
     */
    private boolean isLocalhost(String host) {
        return host.equals("localhost") ||
               host.equals("127.0.0.1") ||
               host.equals("::1") ||
               host.startsWith("192.168.") ||
               host.startsWith("10.") ||
               host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    /**
     * Checks for dangerous patterns in URLs.
     */
    private boolean containsDangerousPatterns(String urlString) {
        // Check for potential script injection patterns
        String lowerUrl = urlString.toLowerCase();

        if (lowerUrl.contains("javascript:") ||
            lowerUrl.contains("data:") ||
            lowerUrl.contains("vbscript:") ||
            lowerUrl.contains("file:")) {
            return true;
        }

        // Check for embedded credentials (security risk)
        if (urlString.contains("@") && urlString.indexOf("@") < urlString.lastIndexOf("/")) {
            log.warn("URL contains embedded credentials: {}", urlString);
            // Don't fail validation, just log warning
        }

        return false;
    }

    /**
     * Checks if URL contains unencoded whitespace characters.
     */
    private boolean containsUnencodedWhitespace(String urlString) {
        // Check for common whitespace characters that should be encoded
        return urlString.contains(" ") ||
               urlString.contains("\t") ||
               urlString.contains("\n") ||
               urlString.contains("\r");
    }

    /**
     * Adds a custom constraint violation message.
     */
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
