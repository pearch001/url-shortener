package com.urlshortener.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA Entity representing a URL mapping between a short code and a long URL.
 * This entity is the core domain model for the URL shortener service.
 *
 * <p>Thread-safety note: The hitCount field is designed to be updated via
 * atomic database operations rather than entity-level updates to avoid
 * race conditions under concurrent access.</p>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(
    name = "url_mappings",
    indexes = {
        @Index(name = "idx_code", columnList = "code", unique = true),
        @Index(name = "idx_long_url", columnList = "long_url"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"id"})
public class UrlMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key for the URL mapping.
     * Auto-generated using the database's identity strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * The unique short code that represents the shortened URL.
     * This is the path segment used in the short URL (e.g., "abc123d").
     *
     * <p>Constraints:
     * <ul>
     *   <li>Must be unique across all URL mappings</li>
     *   <li>Maximum length: 10 characters</li>
     *   <li>Cannot be null or blank</li>
     *   <li>Indexed for fast lookups during redirects</li>
     * </ul>
     */
    @NotBlank(message = "Short code cannot be blank")
    @Size(min = 1, max = 10, message = "Short code must be between 1 and 10 characters")
    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    /**
     * The original long URL that the short code maps to.
     * This is the destination URL where users will be redirected.
     *
     * <p>Constraints:
     * <ul>
     *   <li>Maximum length: 2048 characters (standard URL length limit)</li>
     *   <li>Cannot be null or blank</li>
     *   <li>Indexed for duplicate detection and lookups</li>
     * </ul>
     */
    @NotBlank(message = "Long URL cannot be blank")
    @Size(max = 2048, message = "Long URL must not exceed 2048 characters")
    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    /**
     * Timestamp indicating when this URL mapping was created.
     * Automatically set by Hibernate when the entity is first persisted.
     *
     * <p>This field is immutable after creation and is used for:
     * <ul>
     *   <li>Audit trail</li>
     *   <li>Analytics and reporting</li>
     *   <li>Expiration calculations</li>
     * </ul>
     */
    @NotNull(message = "Creation timestamp cannot be null")
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optional expiration timestamp for the URL mapping.
     * If set, the URL will no longer be accessible after this time.
     *
     * <p>Null values indicate the URL never expires.
     * Indexed to support efficient cleanup queries for expired URLs.
     */
    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;

    /**
     * Counter tracking the number of times this short URL has been accessed.
     * Used for analytics and usage statistics.
     *
     * <p>Thread-safety: This field should NOT be updated via entity setters.
     * Instead, use atomic database operations (e.g., UPDATE ... SET hit_count = hit_count + 1)
     * to avoid lost updates under concurrent access.</p>
     *
     * <p>Defaults to 0 for new URL mappings.</p>
     */
    @NotNull(message = "Hit count cannot be null")
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private Long hitCount = 0L;

    /**
     * Checks if this URL mapping has expired based on the current time.
     *
     * @param currentTime the current time to compare against
     * @return true if the URL has expired, false otherwise
     */
    public boolean isExpired(Instant currentTime) {
        return expiresAt != null && expiresAt.isBefore(currentTime);
    }

    /**
     * Checks if this URL mapping has an expiration date set.
     *
     * @return true if expiration is set, false otherwise
     */
    public boolean hasExpiration() {
        return expiresAt != null;
    }

    /**
     * Increments the hit count by one.
     * Note: This method is provided for convenience but should be used with caution.
     * Prefer atomic database updates for thread-safety in production.
     */
    public void incrementHitCount() {
        this.hitCount++;
    }

    /**
     * Custom equals implementation based on the unique code field.
     * Two UrlMapping entities are considered equal if they have the same code.
     *
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlMapping that = (UrlMapping) o;
        return code != null && code.equals(that.code);
    }

    /**
     * Custom hashCode implementation based on the unique code field.
     * Consistent with the equals method.
     *
     * @return the hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
