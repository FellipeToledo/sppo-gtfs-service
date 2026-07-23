package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.domain.Coordinates;
import com.fajtech.sppogtfs.domain.LineItinerary;
import com.fajtech.sppogtfs.domain.RouteShape;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * GeoJSON representations (RFC 7946): a line is a {@code FeatureCollection}, each shape a
 * {@code Feature} with a {@code LineString} geometry ({@code [lon, lat]} order).
 */
public final class GeoJsonDtos {

    private GeoJsonDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LineString(String type, double[][] coordinates) {
        public static LineString from(RouteShape s) {
            List<Coordinates> pts = s.points();
            double[][] coords = new double[pts.size()][2];
            for (int i = 0; i < pts.size(); i++) {
                coords[i][0] = pts.get(i).longitude();
                coords[i][1] = pts.get(i).latitude();
            }
            return new LineString("LineString", coords);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShapeProperties(String shapeId, Integer directionId, String headsign,
                                  int pointCount, double lengthMeters, BBoxDto bbox) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Feature(String type, LineString geometry, ShapeProperties properties) {
        public static Feature from(RouteShape s) {
            var props = new ShapeProperties(s.shapeId(), s.directionId(), s.headsign(),
                    s.pointCount(), Math.round(s.lengthMeters() * 10.0) / 10.0,
                    BBoxDto.from(s.bbox()));
            return new Feature("Feature", LineString.from(s), props);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeatureCollection(
            String type,
            List<Feature> features,
            // foreign members
            String line,
            String routeLongName,
            String resolution,
            FeedVersionDto feedVersion) {

        public static FeatureCollection from(LineItinerary it) {
            return new FeatureCollection(
                    "FeatureCollection",
                    it.shapes().stream().map(Feature::from).toList(),
                    it.line().value(),
                    it.routeLongName(),
                    it.resolution().name().toLowerCase(),
                    FeedVersionDto.from(it.feedVersion()));
        }
    }

    /** Single-shape GeoJSON feature (for the standalone shape endpoint). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShapeFeature(String type, LineString geometry, ShapeProperties properties) {
        public static ShapeFeature from(RouteShape s) {
            Feature f = Feature.from(s);
            return new ShapeFeature(f.type(), f.geometry(), f.properties());
        }
    }
}
