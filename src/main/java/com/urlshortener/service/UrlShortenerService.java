package com.urlshortener.service;

import com.urlshortener.dto.ShortenUrlRequest;
import com.urlshortener.dto.ShortenUrlResponse;
import com.urlshortener.dto.UrlStatsResponse;

public interface UrlShortenerService {

    ShortenUrlResponse shortenUrl(ShortenUrlRequest request);

    String getOriginalUrl(String shortCode);

    UrlStatsResponse getUrlStats(String shortCode);

    void deleteUrl(String shortCode);

    void cleanupExpiredUrls();
}
