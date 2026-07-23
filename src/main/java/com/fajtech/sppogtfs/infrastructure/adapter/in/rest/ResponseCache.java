package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caffeine cache of already-built response bodies, keyed by ETag (§6.3). Because the ETag
 * derives from {@code feedVersion.id} + line/shape + format + simplify, a feed change makes
 * old keys unreachable and they age out — no explicit invalidation on reload needed.
 */
@Component
public class ResponseCache {

    private final Cache<String, Object> cache;

    public ResponseCache(
            @Value("${sppo.gtfs.cache.max-size:5000}") long maxSize,
            @Value("${sppo.gtfs.cache.ttl-minutes:120}") long ttlMinutes) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofMinutes(ttlMinutes))
                .build();
    }

    public Object get(String etag, Supplier<Object> loader) {
        return cache.get(etag, k -> loader.get());
    }
}
