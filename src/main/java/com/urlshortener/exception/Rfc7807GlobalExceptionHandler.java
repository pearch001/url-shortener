package com.urlshortener.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler implementing RFC 7807 (Problem Details for HTTP APIs).
 * Provides consistent error responses across the entire application.
 *
 * <p>RFC 7807 defines a standard format for error responses with the following fields:
 * <ul>
 *   <li>type: URI identifying the problem type</li>
 *   <li>title: Short, human-readable summary</li>
 *   <li>status: HTTP status code</li>
 *   <li>detail: Human-readable explanation specific to this occurrence</li>
 *   <li>instance: URI identifying the specific occurrence</li>
 * </ul>
 *
 * <p>Example response:
 * <pre>
 * {
 *   "type": "https://api.urlshortener.com/errors/url-not-found",
 *   "title": "URL Not Found",
 *   "status": 404,
 *   "detail": "URL mapping not found for short code: abc123",
 *   "instance": "/api/urls/abc123",
 *   "timestamp": "2026-01-27T10:30:00Z"
 * }
 * </pre>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a>
 */
@RestControllerAdvice
@Slf4j
public class Rfc7807GlobalExceptionHandler {

    private static final String PROBLEM_BASE_URI = "https://api.urlshortener.com/errors";

    /**
     * Handles UrlNotFoundException (404 Not Found).
     * Returns RFC 7807 ProblemDetail with appropriate metadata.
     */
    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUrlNotFoundException(
            UrlNotFoundException ex,
            HttpServletRequest request) {

        log.error("URL not found: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/url-not-found"));
        problemDetail.setTitle("URL Not Found");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        if (ex.getShortCode() != null) {
            problemDetail.setProperty("shortCode", ex.getShortCode());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Handles UrlExpiredException (410 Gone).
     * Indicates the resource existed but is no longer available.
     */
    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ProblemDetail> handleUrlExpiredException(
            UrlExpiredException ex,
            HttpServletRequest request) {

        log.warn("URL expired: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.GONE,
            ex.getMessage()
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/url-expired"));
        problemDetail.setTitle("URL Expired");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        if (ex.getShortCode() != null) {
            problemDetail.setProperty("shortCode", ex.getShortCode());
        }

        if (ex.getExpiredAt() != null) {
            problemDetail.setProperty("expiredAt", ex.getExpiredAt());
        }

        return ResponseEntity.status(HttpStatus.GONE).body(problemDetail);
    }

    /**
     * Handles InvalidUrlException (400 Bad Request).
     * Returns validation error details.
     */
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUrlException(
            InvalidUrlException ex,
            HttpServletRequest request) {

        log.error("Invalid URL: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/invalid-url"));
        problemDetail.setTitle("Invalid URL");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles RateLimitExceededException (429 Too Many Requests).
     * Includes Retry-After header as per RFC 6585.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceededException(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            ex.getMessage()
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/rate-limit-exceeded"));
        problemDetail.setTitle("Rate Limit Exceeded");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("retryAfter", ex.getRetryAfterSeconds());

        if (ex.getClientIdentifier() != null) {
            problemDetail.setProperty("clientIdentifier", ex.getClientIdentifier());
        }

        // Add Retry-After header as per HTTP standard
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(problemDetail);
    }

    /**
     * Handles DuplicateShortCodeException (409 Conflict).
     * Indicates the requested short code already exists.
     */
    @ExceptionHandler(DuplicateShortCodeException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateShortCodeException(
            DuplicateShortCodeException ex,
            HttpServletRequest request) {

        log.error("Duplicate short code: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/duplicate-short-code"));
        problemDetail.setTitle("Duplicate Short Code");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    /**
     * Handles bean validation failures (400 Bad Request).
     * Aggregates all validation errors into a structured response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.error("Validation failed: {}", ex.getMessage());

        // Collect all field errors
        List<Map<String, String>> validationErrors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("field", error.getField());
            errorDetail.put("message", error.getDefaultMessage());
            errorDetail.put("rejectedValue",
                error.getRejectedValue() != null ? error.getRejectedValue().toString() : "null");
            validationErrors.add(errorDetail);
        }

        String detail = String.format("Validation failed for %d field(s)", validationErrors.size());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            detail
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("validationErrors", validationErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles constraint violation exceptions (400 Bad Request).
     * Typically thrown when method-level validation fails.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.error("Constraint violation: {}", ex.getMessage());

        List<String> violations = ex.getConstraintViolations()
            .stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toList());

        String detail = String.format("Constraint violation: %d error(s)", violations.size());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            detail
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/constraint-violation"));
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("violations", violations);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles IllegalArgumentException (400 Bad Request).
     * Typically thrown for invalid method arguments.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.error("Illegal argument: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/illegal-argument"));
        problemDetail.setTitle("Invalid Request Parameter");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles HttpMessageNotReadableException (400 Bad Request).
     * Occurs when the request body cannot be read or parsed (e.g., malformed JSON).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.error("Malformed request body: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Malformed JSON request or invalid request body"
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/malformed-request"));
        problemDetail.setTitle("Malformed Request");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles HttpMediaTypeNotSupportedException (415 Unsupported Media Type).
     * Occurs when the Content-Type header is not supported.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        log.error("Unsupported media type: {}", ex.getContentType());

        String supportedTypes = ex.getSupportedMediaTypes().stream()
            .map(Object::toString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("application/json");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type: " + ex.getContentType() + ". Supported types: " + supportedTypes
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/unsupported-media-type"));
        problemDetail.setTitle("Unsupported Media Type");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("supportedMediaTypes", ex.getSupportedMediaTypes());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problemDetail);
    }

    /**
     * Handles all other unhandled exceptions (500 Internal Server Error).
     * Logs the full stack trace but returns a generic error message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later."
        );

        problemDetail.setType(URI.create(PROBLEM_BASE_URI + "/internal-error"));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        // In development, include exception type for debugging
        if (isDevelopmentEnvironment()) {
            problemDetail.setProperty("exceptionType", ex.getClass().getSimpleName());
            problemDetail.setProperty("exceptionMessage", ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Determines if the application is running in development mode.
     * Can be enhanced to check Spring profiles.
     */
    private boolean isDevelopmentEnvironment() {
        // TODO: Check Spring active profiles
        return true; // Default to true for now
    }
}
