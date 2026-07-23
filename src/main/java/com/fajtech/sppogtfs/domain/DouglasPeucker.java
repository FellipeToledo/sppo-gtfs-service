package com.fajtech.sppogtfs.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Ramer–Douglas–Peucker simplification with a tolerance expressed in meters.
 *
 * <p>Perpendicular distance is approximated on a local equirectangular projection
 * around each segment — accurate at city scale and far cheaper than full geodesics.
 * Simplification alters the corridor test; the API keeps it opt-in and conservative.
 */
public final class DouglasPeucker {

    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private DouglasPeucker() {
    }

    public static List<Coordinates> simplify(List<Coordinates> points, double toleranceMeters) {
        if (toleranceMeters <= 0 || points == null || points.size() < 3) {
            return points;
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyRange(points, 0, points.size() - 1, toleranceMeters, keep);

        List<Coordinates> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                out.add(points.get(i));
            }
        }
        return out;
    }

    private static void simplifyRange(List<Coordinates> pts, int first, int last,
                                      double tolerance, boolean[] keep) {
        if (last <= first + 1) {
            return;
        }
        double maxDist = -1.0;
        int index = -1;
        Coordinates a = pts.get(first);
        Coordinates b = pts.get(last);
        for (int i = first + 1; i < last; i++) {
            double d = perpendicularMeters(pts.get(i), a, b);
            if (d > maxDist) {
                maxDist = d;
                index = i;
            }
        }
        if (maxDist > tolerance) {
            keep[index] = true;
            simplifyRange(pts, first, index, tolerance, keep);
            simplifyRange(pts, index, last, tolerance, keep);
        }
    }

    private static double perpendicularMeters(Coordinates p, Coordinates a, Coordinates b) {
        double latRad = Math.toRadians((a.latitude() + b.latitude()) / 2.0);
        double mPerDegLon = METERS_PER_DEG_LAT * Math.cos(latRad);

        double px = p.longitude() * mPerDegLon;
        double py = p.latitude() * METERS_PER_DEG_LAT;
        double ax = a.longitude() * mPerDegLon;
        double ay = a.latitude() * METERS_PER_DEG_LAT;
        double bx = b.longitude() * mPerDegLon;
        double by = b.latitude() * METERS_PER_DEG_LAT;

        double dx = bx - ax;
        double dy = by - ay;
        double segLenSq = dx * dx + dy * dy;
        if (segLenSq == 0.0) {
            double ex = px - ax;
            double ey = py - ay;
            return Math.sqrt(ex * ex + ey * ey);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / segLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double projX = ax + t * dx;
        double projY = ay + t * dy;
        double ex = px - projX;
        double ey = py - projY;
        return Math.sqrt(ex * ex + ey * ey);
    }
}
