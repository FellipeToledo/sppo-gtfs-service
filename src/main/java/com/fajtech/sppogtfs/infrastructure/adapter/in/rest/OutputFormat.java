package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import java.util.Locale;

/** Response geometry format. */
public enum OutputFormat {
    ENCODED, GEOJSON;

    public static OutputFormat parse(String raw, OutputFormat fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "encoded" -> ENCODED;
            case "geojson" -> GEOJSON;
            default -> throw new IllegalArgumentException(
                    "unknown format '" + raw + "' (expected: encoded|geojson)");
        };
    }
}
