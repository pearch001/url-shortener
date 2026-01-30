package com.urlshortener.exception;

/**
 * Exception thrown when code generation fails after maximum retry attempts.
 * This typically indicates a high collision rate or misconfiguration.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
public class CodeGenerationException extends RuntimeException {

    /**
     * Constructs a new CodeGenerationException with the specified message.
     *
     * @param message the detail message
     */
    public CodeGenerationException(String message) {
        super(message);
    }

    /**
     * Constructs a new CodeGenerationException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public CodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
