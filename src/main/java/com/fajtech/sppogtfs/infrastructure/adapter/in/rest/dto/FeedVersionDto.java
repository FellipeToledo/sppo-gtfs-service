package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.domain.FeedVersion;

import java.time.Instant;

public record FeedVersionDto(String id, Instant publishedAt, String source) {

    public static FeedVersionDto from(FeedVersion v) {
        return new FeedVersionDto(v.id(), v.publishedAt(), v.source());
    }
}
