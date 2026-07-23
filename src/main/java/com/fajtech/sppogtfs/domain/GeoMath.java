package com.fajtech.sppogtfs.domain;

import java.util.List;

/** Pure geodesic helpers. */
public final class GeoMath {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoMath() {
    }

    /** Great-circle distance between two coordinates, in meters (haversine). */
    public static double haversineMeters(Coordinates a, Coordinates b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** Accumulated haversine length of an ordered polyline, in meters. */
    public static double polylineLengthMeters(List<Coordinates> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineMeters(points.get(i - 1), points.get(i));
        }
        return total;
    }
}
