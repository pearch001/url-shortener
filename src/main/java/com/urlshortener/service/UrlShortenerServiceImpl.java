package com.urlshortener.service;

import com.urlshortener.dto.ShortenUrlRequest;
import com.urlshortener.dto.ShortenUrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.DuplicateShortCodeException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerServiceImpl implements UrlShortenerService {

    private final UrlMappingRepository urlMappingRepository;
    private final ShortCodeGenerator shortCodeGenerator;

    @Value("${urlshortener.base-url}")
    private String baseUrl;

    @Value("${urlshortener.url.expiration-days:365}")
    private int defaultExpirationDays;

    @Override
    @Transactional
    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request) {
        log.info("Shortening URL: {}", request.getUrl());

        // Check if URL already exists
        var existingUrl = urlMappingRepository.findByLongUrl(request.getUrl());
        if (existingUrl.isPresent() && !existingUrl.get().isExpired(Instant.now())) {
            log.info("URL already shortened: {}", request.getUrl());
            return buildResponse(existingUrl.get());
        }

        String shortCode = generateUniqueShortCode(request.getCustomAlias());

        Instant expiresAt = calculateExpirationDate(request.getExpirationDays());

        UrlMapping urlMapping = UrlMapping.builder()
                .longUrl(request.getUrl())
                .code(shortCode)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .hitCount(0L)
                .build();

        UrlMapping savedUrl = urlMappingRepository.save(urlMapping);
        log.info("URL shortened successfully: {} -> {}", request.getUrl(), shortCode);

        return buildResponse(savedUrl);
    }

    @Override
    @Transactional
    public String getOriginalUrl(String shortCode) {
        log.info("Retrieving original URL for short code: {}", shortCode);

        UrlMapping urlMapping = urlMappingRepository.findByCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        if (urlMapping.isExpired(Instant.now())) {
            log.warn("URL has expired: {}", shortCode);
            throw new UrlExpiredException("This short URL has expired");
        }

        // Increment access count atomically
        urlMappingRepository.incrementHitCount(shortCode);

        log.info("Redirecting {} to {}", shortCode, urlMapping.getLongUrl());
        return urlMapping.getLongUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public UrlStatsResponse getUrlStats(String shortCode) {
        log.info("Retrieving stats for short code: {}", shortCode);

        UrlMapping urlMapping = urlMappingRepository.findByCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        return UrlStatsResponse.builder()
                .shortCode(urlMapping.getCode())
                .originalUrl(urlMapping.getLongUrl())
                .accessCount(urlMapping.getHitCount())
                .createdAt(urlMapping.getCreatedAt())
                .lastAccessedAt(null) // Note: UrlMapping doesn't track lastAccessedAt
                .expiresAt(urlMapping.getExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public void deleteUrl(String shortCode) {
        log.info("Deleting URL with short code: {}", shortCode);

        UrlMapping urlMapping = urlMappingRepository.findByCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        urlMappingRepository.delete(urlMapping);
        log.info("URL deleted successfully: {}", shortCode);
    }

    @Override
    @Transactional
    public void cleanupExpiredUrls() {
        log.info("Cleaning up expired URLs");
        int deletedCount = urlMappingRepository.deleteExpiredUrls(Instant.now());
        log.info("Deleted {} expired URLs", deletedCount);
    }

    private String generateUniqueShortCode(String customAlias) {
        if (customAlias != null && !customAlias.isBlank()) {
            if (urlMappingRepository.existsByCode(customAlias)) {
                throw new DuplicateShortCodeException("Custom alias already exists: " + customAlias);
            }
            return customAlias;
        }

        String shortCode;
        int attempts = 0;
        int maxAttempts = 10;

        do {
            shortCode = shortCodeGenerator.generate();
            attempts++;
        } while (urlMappingRepository.existsByCode(shortCode) && attempts < maxAttempts);

        if (attempts >= maxAttempts) {
            throw new RuntimeException("Failed to generate unique short code after " + maxAttempts + " attempts");
        }

        return shortCode;
    }

    private Instant calculateExpirationDate(Integer expirationDays) {
        int days = (expirationDays != null && expirationDays > 0)
                ? expirationDays
                : defaultExpirationDays;
        return Instant.now().plus(days, java.time.temporal.ChronoUnit.DAYS);
    }

    private ShortenUrlResponse buildResponse(UrlMapping urlMapping) {
        return ShortenUrlResponse.builder()
                .shortUrl(baseUrl + "/r/" + urlMapping.getCode())
                .shortCode(urlMapping.getCode())
                .originalUrl(urlMapping.getLongUrl())
                .createdAt(urlMapping.getCreatedAt())
                .expiresAt(urlMapping.getExpiresAt())
                .build();
    }
}
