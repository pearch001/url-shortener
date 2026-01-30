package com.urlshortener.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter/wrapper for the new CodeGenerator.
 * Provides backward compatibility with existing code.
 *
 * @deprecated Use {@link CodeGenerator} directly for new code
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Deprecated(since = "1.0", forRemoval = true)
public class ShortCodeGenerator {

    private final CodeGenerator codeGenerator;

    /**
     * Generates a random short code.
     * Delegates to the new CodeGenerator.
     *
     * @return generated short code
     * @deprecated Use {@link CodeGenerator#generateCode(String)} instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public String generate() {
        log.debug("Using deprecated ShortCodeGenerator.generate() - consider using CodeGenerator directly");
        return codeGenerator.generateCode("");
    }
}
