package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.domain.BoundingBox;

public record BBoxDto(double minLat, double minLon, double maxLat, double maxLon) {

    public static BBoxDto from(BoundingBox b) {
        return new BBoxDto(b.minLat(), b.minLon(), b.maxLat(), b.maxLon());
    }
}
