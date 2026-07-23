package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.FeedSnapshot;

import java.time.Instant;
import java.util.Map;

/** {@code GET /feed/version} response (§4.4). */
public record FeedVersionResponse(String id, Instant publishedAt, String source,
                                  Map<String, Long> counts) {

    public static FeedVersionResponse from(FeedSnapshot s) {
        return new FeedVersionResponse(
                s.feedVersion().id(),
                s.feedVersion().publishedAt(),
                s.feedVersion().source(),
                Map.of("lines", s.linesIndexed(), "shapes", s.shapesIndexed()));
    }
}
