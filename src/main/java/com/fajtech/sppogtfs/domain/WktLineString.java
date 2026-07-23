package com.fajtech.sppogtfs.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal parser for WKT {@code LINESTRING} geometries as produced by BigQuery
 * {@code ST_ASTEXT} over the SMTR {@code shapes_geom.wkt_shape} column.
 *
 * <p>WKT uses {@code lon lat} order (X Y), WGS84. Accepts an optional {@code SRID=...;}
 * prefix (EWKT), the {@code Z}/{@code M} tags, and arbitrary internal whitespace.
 * Only {@code LINESTRING} is supported — the shapes feed contains line geometries.
 */
public final class WktLineString {

    private WktLineString() {
    }

    public static List<Coordinates> parse(String wkt) {
        if (wkt == null || wkt.isBlank()) {
            throw new IllegalArgumentException("empty WKT");
        }
        String s = wkt.trim();

        // Optional EWKT SRID prefix: "SRID=4326;LINESTRING(...)"
        int semi = s.indexOf(';');
        if (semi >= 0 && s.regionMatches(true, 0, "SRID=", 0, 5)) {
            s = s.substring(semi + 1).trim();
        }

        if (!s.regionMatches(true, 0, "LINESTRING", 0, "LINESTRING".length())) {
            throw new IllegalArgumentException("not a LINESTRING: " + preview(s));
        }
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("malformed LINESTRING: " + preview(s));
        }
        String body = s.substring(open + 1, close).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("LINESTRING has no points");
        }

        List<Coordinates> points = new ArrayList<>();
        for (String pair : body.split(",")) {
            String p = pair.trim();
            if (p.isEmpty()) {
                continue;
            }
            String[] parts = p.split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException("bad coordinate: '" + p + "'");
            }
            double lon = Double.parseDouble(parts[0]); // X
            double lat = Double.parseDouble(parts[1]); // Y
            points.add(new Coordinates(lat, lon));
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("LINESTRING parsed to no points");
        }
        return points;
    }

    private static String preview(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
