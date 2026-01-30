package com.urlshortener.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for Base62 encoding operations.
 * Base62 uses characters [0-9a-zA-Z] providing 62 possible values per position.
 *
 * <p>Base62 is ideal for URL shortening because:
 * <ul>
 *   <li>Compact representation (62^7 = ~3.5 trillion combinations)</li>
 *   <li>Case-sensitive alphanumeric characters only</li>
 *   <li>URL-safe without encoding</li>
 *   <li>Human-readable and easily shareable</li>
 * </ul>
 *
 * <p>Character set: "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
 *
 * <p>This class is thread-safe and stateless.
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public final class Base62Encoder {

    /**
     * Base62 character set: digits (0-9), lowercase (a-z), uppercase (A-Z).
     * Total: 62 characters.
     */
    private static final String BASE62_CHARSET =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Base value for Base62 encoding.
     */
    private static final int BASE = 62;

    /**
     * Array for fast character lookup during encoding.
     */
    private static final char[] BASE62_CHARS = BASE62_CHARSET.toCharArray();

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private Base62Encoder() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    /**
     * Encodes a positive long value to Base62 string.
     * Uses mathematical conversion: repeatedly divide by 62 and map remainders.
     *
     * <p>Algorithm:
     * <pre>
     * 1. While number > 0:
     *    a. remainder = number % 62
     *    b. prepend BASE62_CHARS[remainder] to result
     *    c. number = number / 62
     * 2. Return result string
     * </pre>
     *
     * <p>Example: 12345 -> "3D7"
     * <ul>
     *   <li>12345 % 62 = 7 -> '7'</li>
     *   <li>199 % 62 = 13 -> 'D'</li>
     *   <li>3 % 62 = 3 -> '3'</li>
     *   <li>Result: "3D7"</li>
     * </ul>
     *
     * @param value the positive long value to encode
     * @return Base62-encoded string
     * @throws IllegalArgumentException if value is negative
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative: " + value);
        }

        if (value == 0) {
            return String.valueOf(BASE62_CHARS[0]);
        }

        StringBuilder encoded = new StringBuilder();

        while (value > 0) {
            int remainder = (int) (value % BASE);
            encoded.insert(0, BASE62_CHARS[remainder]);
            value = value / BASE;
        }

        return encoded.toString();
    }

    /**
     * Encodes a positive long value to Base62 string with fixed length.
     * Pads with leading zeros if necessary.
     *
     * @param value  the positive long value to encode
     * @param length the desired length of the encoded string
     * @return Base62-encoded string with specified length
     * @throws IllegalArgumentException if value is negative or encoded length exceeds specified length
     */
    public static String encode(long value, int length) {
        String encoded = encode(value);

        if (encoded.length() > length) {
            throw new IllegalArgumentException(
                String.format("Encoded value '%s' exceeds specified length %d", encoded, length)
            );
        }

        // Pad with leading zeros
        while (encoded.length() < length) {
            encoded = BASE62_CHARS[0] + encoded;
        }

        return encoded;
    }

    /**
     * Decodes a Base62 string to its original long value.
     * Inverse operation of {@link #encode(long)}.
     *
     * <p>Algorithm:
     * <pre>
     * 1. Initialize result = 0
     * 2. For each character from left to right:
     *    a. Find character index in BASE62_CHARSET
     *    b. result = result * 62 + index
     * 3. Return result
     * </pre>
     *
     * @param encoded the Base62-encoded string
     * @return the decoded long value
     * @throws IllegalArgumentException if string contains invalid characters
     */
    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string cannot be null or empty");
        }

        long decoded = 0;

        for (char c : encoded.toCharArray()) {
            int index = BASE62_CHARSET.indexOf(c);

            if (index == -1) {
                throw new IllegalArgumentException(
                    String.format("Invalid Base62 character: '%c' in string '%s'", c, encoded)
                );
            }

            decoded = decoded * BASE + index;
        }

        return decoded;
    }

    /**
     * Encodes a byte array to Base62 string.
     * Useful for encoding hash digests.
     *
     * <p>Converts byte array to a large positive number and encodes it.
     *
     * @param bytes the byte array to encode
     * @return Base62-encoded string
     */
    public static String encodeBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Byte array cannot be null or empty");
        }

        // Convert bytes to positive BigInteger equivalent
        StringBuilder result = new StringBuilder();
        long value = 0;

        // Process bytes in chunks to avoid overflow
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }

        // Make sure the value is positive
        value = Math.abs(value);

        return encode(value);
    }

    /**
     * Generates a Base62 code from a hash digest (MD5, SHA-256, etc.).
     * Takes the first N characters of the Base62-encoded hash.
     *
     * @param input  the input string to hash
     * @param length the desired code length
     * @return Base62-encoded code of specified length
     */
    public static String encodeHash(String input, int length) {
        return encodeHash(input, length, "SHA-256");
    }

    /**
     * Generates a Base62 code from a hash digest with specified algorithm.
     *
     * @param input     the input string to hash
     * @param length    the desired code length
     * @param algorithm the hash algorithm (MD5, SHA-256, SHA-512)
     * @return Base62-encoded code of specified length
     * @throws IllegalStateException if hash algorithm is not available
     */
    public static String encodeHash(String input, int length, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert hash to positive long
            long hashValue = 0;
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                hashValue = (hashValue << 8) | (hash[i] & 0xFF);
            }

            // Make positive
            hashValue = Math.abs(hashValue);

            // Encode and take first N characters
            String encoded = encode(hashValue);

            if (encoded.length() >= length) {
                return encoded.substring(0, length);
            } else {
                // Pad with leading zeros if necessary
                return encode(hashValue, length);
            }

        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm not available: {}", algorithm, e);
            throw new IllegalStateException("Hash algorithm not available: " + algorithm, e);
        }
    }

    /**
     * Validates if a string is a valid Base62-encoded string.
     *
     * @param encoded the string to validate
     * @return true if valid Base62, false otherwise
     */
    public static boolean isValidBase62(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return false;
        }

        for (char c : encoded.toCharArray()) {
            if (BASE62_CHARSET.indexOf(c) == -1) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the character set used for Base62 encoding.
     *
     * @return Base62 character set
     */
    public static String getCharset() {
        return BASE62_CHARSET;
    }

    /**
     * Calculates the maximum value that can be represented with N characters.
     *
     * @param length the number of characters
     * @return maximum representable value (62^length - 1)
     */
    public static long maxValueForLength(int length) {
        return (long) Math.pow(BASE, length) - 1;
    }

    /**
     * Calculates the minimum length required to represent a value.
     *
     * @param value the value to represent
     * @return minimum required length
     */
    public static int minLengthForValue(long value) {
        if (value == 0) {
            return 1;
        }

        return (int) Math.ceil(Math.log(value + 1) / Math.log(BASE));
    }
}
