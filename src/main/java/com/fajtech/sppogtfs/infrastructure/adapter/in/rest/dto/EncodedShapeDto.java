package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.domain.RouteSegment;
import com.fajtech.sppogtfs.domain.RouteShape;
import com.fajtech.sppogtfs.domain.PolylineEncoder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Shape serialized as a Google encoded polyline (default, compact form). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncodedShapeDto(
        String shapeId,
        Integer directionId,
        String sentido,
        String evento,
        String encodedPolyline,
        BBoxDto bbox,
        int pointCount,
        double lengthMeters,
        List<SegmentDto> segments) {

    public static EncodedShapeDto from(RouteShape s) {
        return new EncodedShapeDto(
                s.shapeId(),
                s.directionId(),
                s.sentido(),
                s.evento(),
                s.encodedPolyline(),
                BBoxDto.from(s.bbox()),
                s.pointCount(),
                round(s.lengthMeters()),
                s.segments().stream().map(SegmentDto::from).toList());
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /**
     * One segment of the shape, carrying SMTR's flags <b>verbatim</b>. {@code disregarded}
     * is the only authoritative exclusion; the others are informative — recombining them
     * would exclude route that SMTR does validate (77 tunnel segments are not disregarded).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SegmentDto(
            String segmentId,
            String encodedPolyline,
            int pointCount,
            double lengthMeters,
            boolean disregarded,
            boolean tunnel,
            boolean smallSegment,
            boolean damagedBuffer) {

        static SegmentDto from(RouteSegment seg) {
            return new SegmentDto(
                    seg.segmentId(),
                    PolylineEncoder.encode(seg.points()),
                    seg.points().size(),
                    Math.round(seg.lengthMeters() * 10.0) / 10.0,
                    seg.disregarded(),
                    seg.tunnel(),
                    seg.smallSegment(),
                    seg.damagedBuffer());
        }
    }
}
