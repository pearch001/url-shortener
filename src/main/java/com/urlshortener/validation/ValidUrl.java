package com.urlshortener.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for URL validation.
 * Validates that a string is a well-formed URL with supported protocols.
 *
 * <p>This annotation checks:
 * <ul>
 *   <li>URL format validity using {@link java.net.URL}</li>
 *   <li>Protocol restrictions (HTTP and HTTPS only)</li>
 *   <li>URL length constraints</li>
 *   <li>Presence of host component</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * public class CreateUrlRequest {
 *     &#64;ValidUrl
 *     private String longUrl;
 * }
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 * @see UrlValidator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UrlValidator.class)
@Documented
public @interface ValidUrl {

    /**
     * Default validation error message.
     *
     * @return the error message template
     */
    String message() default "Invalid URL format. Must be a valid HTTP or HTTPS URL";

    /**
     * Validation groups for conditional validation.
     *
     * @return the validation groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients to assign custom metadata.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * Allowed URL protocols.
     * Defaults to HTTP and HTTPS.
     *
     * @return array of allowed protocols
     */
    String[] protocols() default {"http", "https"};

    /**
     * Maximum allowed URL length.
     * Defaults to 2048 characters.
     *
     * @return maximum URL length
     */
    int maxLength() default 2048;

    /**
     * Minimum allowed URL length.
     * Defaults to 10 characters (e.g., "http://a.b").
     *
     * @return minimum URL length
     */
    int minLength() default 10;
}
