package com.urlshortener.repository;

import com.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UrlMapping} entity.
 * Provides CRUD operations and custom query methods for URL mapping persistence.
 *
 * <p>This repository follows Spring Data JPA conventions and includes:
 * <ul>
 *   <li>Standard CRUD operations (inherited from JpaRepository)</li>
 *   <li>Query derivation methods for common lookups</li>
 *   <li>Custom JPQL queries for complex operations</li>
 *   <li>Atomic update operations for thread-safety</li>
 * </ul>
 *
 * <p>All query methods are executed within a transactional context
 * when invoked through a Spring-managed service layer.</p>
 *
 * @author URL Shortener Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Finds a URL mapping by its unique short code.
     * This is the primary lookup method used during URL redirects.
     *
     * <p>Performance: Leverages the unique index on the code column
     * for optimal query performance (O(log n)).</p>
     *
     * @param code the short code to search for (case-sensitive)
     * @return an Optional containing the UrlMapping if found, empty otherwise
     * @throws IllegalArgumentException if code is null
     */
    Optional<UrlMapping> findByCode(String code);

    /**
     * Finds a URL mapping by its original long URL.
     * Used for duplicate detection when creating new short URLs.
     *
     * <p>Note: This method returns the first match if multiple mappings
     * exist for the same long URL. Consider using findAllByLongUrl()
     * if multiple results are expected.</p>
     *
     * <p>Performance: Uses the index on long_url column for efficient lookups.</p>
     *
     * @param longUrl the original long URL to search for
     * @return an Optional containing the first matching UrlMapping if found, empty otherwise
     * @throws IllegalArgumentException if longUrl is null
     */
    Optional<UrlMapping> findByLongUrl(String longUrl);

    /**
     * Finds all URL mappings for a given long URL.
     * Useful when multiple short codes point to the same destination.
     *
     * @param longUrl the original long URL to search for
     * @return a list of all UrlMapping entities with the specified long URL
     * @throws IllegalArgumentException if longUrl is null
     */
    List<UrlMapping> findAllByLongUrl(String longUrl);

    /**
     * Checks if a URL mapping with the given short code already exists.
     * More efficient than findByCode() when only existence check is needed.
     *
     * <p>This method executes a COUNT query internally, which is more
     * efficient than retrieving the full entity.</p>
     *
     * @param code the short code to check
     * @return true if a mapping with this code exists, false otherwise
     * @throws IllegalArgumentException if code is null
     */
    boolean existsByCode(String code);

    /**
     * Checks if a URL mapping with the given long URL already exists.
     *
     * @param longUrl the long URL to check
     * @return true if a mapping with this long URL exists, false otherwise
     * @throws IllegalArgumentException if longUrl is null
     */
    boolean existsByLongUrl(String longUrl);

    /**
     * Finds all URL mappings that have expired as of the given timestamp.
     * Used by cleanup jobs to remove stale data.
     *
     * <p>Performance: Leverages the index on expires_at column.
     * Only returns mappings where expires_at is not null and is before the specified time.</p>
     *
     * @param now the current timestamp to compare against
     * @return a list of expired UrlMapping entities
     * @throws IllegalArgumentException if now is null
     */
    @Query("SELECT u FROM UrlMapping u WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now")
    List<UrlMapping> findExpiredUrls(@Param("now") Instant now);

    /**
     * Atomically increments the hit count for a URL mapping identified by its code.
     * This operation is thread-safe and prevents lost updates under concurrent access.
     *
     * <p>Implementation note: This uses a native UPDATE query that performs
     * the increment at the database level, avoiding the need to fetch and
     * modify the entity in application memory.</p>
     *
     * <p>Usage: Must be called within a transactional context.
     * Use @Transactional annotation on the calling service method.</p>
     *
     * @param code the short code of the URL mapping to update
     * @return the number of entities updated (should be 1 if code exists, 0 otherwise)
     * @throws IllegalArgumentException if code is null
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.hitCount = u.hitCount + 1 WHERE u.code = :code")
    int incrementHitCount(@Param("code") String code);

    /**
     * Atomically increments the hit count by a specific amount.
     * Useful for batch updates or testing scenarios.
     *
     * @param code the short code of the URL mapping to update
     * @param incrementBy the amount to increment by
     * @return the number of entities updated
     * @throws IllegalArgumentException if code is null or incrementBy is negative
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.hitCount = u.hitCount + :incrementBy WHERE u.code = :code")
    int incrementHitCountBy(@Param("code") String code, @Param("incrementBy") long incrementBy);

    /**
     * Deletes all expired URL mappings as of the given timestamp.
     * More efficient than fetching and deleting individually.
     *
     * <p>Usage: Must be called within a transactional context.
     * Recommended to use @Transactional annotation on the calling service method.</p>
     *
     * @param now the current timestamp to compare against
     * @return the number of entities deleted
     * @throws IllegalArgumentException if now is null
     */
    @Modifying
    @Query("DELETE FROM UrlMapping u WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now")
    int deleteExpiredUrls(@Param("now") Instant now);

    /**
     * Finds URL mappings with hit count greater than or equal to the specified threshold.
     * Useful for analytics and identifying popular URLs.
     *
     * @param threshold the minimum hit count threshold
     * @return a list of UrlMapping entities meeting the criteria
     */
    @Query("SELECT u FROM UrlMapping u WHERE u.hitCount >= :threshold ORDER BY u.hitCount DESC")
    List<UrlMapping> findPopularUrls(@Param("threshold") long threshold);

    /**
     * Finds the most recently created URL mappings.
     * Useful for dashboard displays and recent activity feeds.
     *
     * @param limit the maximum number of results to return
     * @return a list of the most recent UrlMapping entities
     */
    @Query("SELECT u FROM UrlMapping u ORDER BY u.createdAt DESC LIMIT :limit")
    List<UrlMapping> findRecentUrls(@Param("limit") int limit);

    /**
     * Counts the total number of URL redirects (sum of all hit counts).
     * Useful for aggregate statistics.
     *
     * @return the total number of redirects across all URL mappings
     */
    @Query("SELECT COALESCE(SUM(u.hitCount), 0) FROM UrlMapping u")
    Long getTotalRedirectCount();

    /**
     * Counts the number of URL mappings created within a specific time range.
     *
     * @param startTime the start of the time range (inclusive)
     * @param endTime the end of the time range (exclusive)
     * @return the count of URL mappings created in the specified range
     */
    @Query("SELECT COUNT(u) FROM UrlMapping u WHERE u.createdAt >= :startTime AND u.createdAt < :endTime")
    Long countUrlsCreatedBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Finds URL mappings that will expire within a specified time window.
     * Useful for notification systems or proactive renewal prompts.
     *
     * @param now the current timestamp
     * @param futureTime the future timestamp defining the window end
     * @return a list of UrlMapping entities expiring within the window
     */
    @Query("SELECT u FROM UrlMapping u WHERE u.expiresAt IS NOT NULL AND u.expiresAt > :now AND u.expiresAt <= :futureTime")
    List<UrlMapping> findUrlsExpiringBetween(@Param("now") Instant now, @Param("futureTime") Instant futureTime);

    /**
     * Updates the expiration time for a specific URL mapping.
     * Allows extending or modifying the expiration without fetching the entity.
     *
     * @param code the short code of the URL mapping to update
     * @param newExpiresAt the new expiration timestamp
     * @return the number of entities updated
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.expiresAt = :newExpiresAt WHERE u.code = :code")
    int updateExpirationTime(@Param("code") String code, @Param("newExpiresAt") Instant newExpiresAt);

    /**
     * Counts the number of active (non-expired) URL mappings.
     * A URL is considered active if it has no expiration date or its expiration is in the future.
     *
     * @param now the current timestamp to compare against
     * @return the count of active URL mappings
     */
    @Query("SELECT COUNT(u) FROM UrlMapping u WHERE u.expiresAt IS NULL OR u.expiresAt > :now")
    Long countActiveUrls(@Param("now") Instant now);
}
