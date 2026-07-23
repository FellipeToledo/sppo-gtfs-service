package com.fajtech.sppogtfs.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Encoded Polyline Algorithm Format, precision 5.
 * ~10x more compact than raw floats in GeoJSON.
 */
public final class PolylineEncoder {

    private static final double FACTOR = 1e5;

    private PolylineEncoder() {
    }

    public static String encode(List<Coordinates> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0;
        long prevLon = 0;
        for (Coordinates c : points) {
            long lat = Math.round(c.latitude() * FACTOR);
            long lon = Math.round(c.longitude() * FACTOR);
            encodeValue(lat - prevLat, sb);
            encodeValue(lon - prevLon, sb);
            prevLat = lat;
            prevLon = lon;
        }
        return sb.toString();
    }

    /** Inverse of {@link #encode}. Provided for round-trip testing / tooling. */
    public static List<Coordinates> decode(String encoded) {
        List<Coordinates> out = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lon = 0;
        while (index < encoded.length()) {
            long[] latRes = decodeValue(encoded, index);
            lat += latRes[0];
            index = (int) latRes[1];
            long[] lonRes = decodeValue(encoded, index);
            lon += lonRes[0];
            index = (int) lonRes[1];
            out.add(new Coordinates(lat / FACTOR, lon / FACTOR));
        }
        return out;
    }

    private static void encodeValue(long value, StringBuilder sb) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (int) (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) ((int) v + 63));
    }

    private static long[] decodeValue(String encoded, int index) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[]{value, index};
    }
}
