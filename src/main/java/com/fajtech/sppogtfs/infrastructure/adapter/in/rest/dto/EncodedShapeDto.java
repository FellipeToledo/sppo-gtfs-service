package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.domain.RouteShape;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Shape serialized as a Google encoded polyline (default, compact form). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncodedShapeDto(
        String shapeId,
        Integer directionId,
        String headsign,
        String encodedPolyline,
        BBoxDto bbox,
        int pointCount,
        double lengthMeters) {

    public static EncodedShapeDto from(RouteShape s) {
        return new EncodedShapeDto(
                s.shapeId(),
                s.directionId(),
                s.headsign(),
                s.encodedPolyline(),
                BBoxDto.from(s.bbox()),
                s.pointCount(),
                round(s.lengthMeters()));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
