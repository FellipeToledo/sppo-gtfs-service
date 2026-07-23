package com.fajtech.sppogtfs.domain;

import java.util.List;

/**
 * Axis-aligned min/max lat-lon envelope. Cheap spatial pre-filter for the backend:
 * positions far from the box are discarded before the point→segment corridor test.
 */
public record BoundingBox(double minLat, double minLon, double maxLat, double maxLon) {

    public BoundingBox {
        if (minLat > maxLat) {
            throw new IllegalArgumentException("minLat > maxLat");
        }
        if (minLon > maxLon) {
            throw new IllegalArgumentException("minLon > maxLon");
        }
    }

    /** Build the tightest box enclosing all points. Requires a non-empty list. */
    public static BoundingBox of(List<Coordinates> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("cannot compute bbox of empty point list");
        }
        double minLat = Double.POSITIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (Coordinates c : points) {
            minLat = Math.min(minLat, c.latitude());
            maxLat = Math.max(maxLat, c.latitude());
            minLon = Math.min(minLon, c.longitude());
            maxLon = Math.max(maxLon, c.longitude());
        }
        return new BoundingBox(minLat, minLon, maxLat, maxLon);
    }
}
