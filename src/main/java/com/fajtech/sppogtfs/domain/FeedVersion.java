package com.fajtech.sppogtfs.domain;

import java.time.Instant;

/**
 * Feed version stamp. The backend uses {@link #id()} as part of its cache key so that
 * a monthly GTFS refresh invalidates caches naturally.
 */
public record FeedVersion(String id, Instant publishedAt, String source) {

    public FeedVersion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("feed version id must not be blank");
        }
    }
}
