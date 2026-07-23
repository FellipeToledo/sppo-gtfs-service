package com.fajtech.sppogtfs.domain;

import java.util.List;

/**
 * One itinerary polyline. Points are ordered as they appear in the source geometry
 * (SMTR {@code shapes_geom.wkt_shape}). {@code lengthMeters} and {@code bbox} are derived.
 *
 * <p>SMTR describes direction with {@code sentido} (e.g. {@code "I"} ida / {@code "V"} volta);
 * {@code directionId} is the GTFS-style 0/1 mapping when it can be derived. {@code evento}
 * marks a non-regular / alternative trajectory ({@code null} = regular).
 */
public record RouteShape(
        String shapeId,
        Integer directionId,
        String sentido,
        String evento,
        List<Coordinates> points,
        double lengthMeters,
        BoundingBox bbox) {

    public RouteShape {
        if (shapeId == null || shapeId.isBlank()) {
            throw new IllegalArgumentException("shapeId must not be blank");
        }
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("shape must have at least one point: " + shapeId);
        }
        points = List.copyOf(points);
    }

    /** Build from ordered points, deriving length and bbox. */
    public static RouteShape of(String shapeId, Integer directionId, String sentido,
                                String evento, List<Coordinates> points) {
        return new RouteShape(
                shapeId,
                directionId,
                sentido,
                evento,
                points,
                GeoMath.polylineLengthMeters(points),
                BoundingBox.of(points));
    }

    public int pointCount() {
        return points.size();
    }

    /** Google encoded polyline (precision 5) of the current points. */
    public String encodedPolyline() {
        return PolylineEncoder.encode(points);
    }

    /**
     * Return a copy simplified to the given tolerance (meters), recomputing length/bbox.
     * A non-positive tolerance returns this instance unchanged.
     */
    public RouteShape simplified(double toleranceMeters) {
        if (toleranceMeters <= 0) {
            return this;
        }
        List<Coordinates> reduced = DouglasPeucker.simplify(points, toleranceMeters);
        if (reduced.size() == points.size()) {
            return this;
        }
        return RouteShape.of(shapeId, directionId, sentido, evento, reduced);
    }
}
