package com.fajtech.sppogtfs.domain;

/**
 * Immutable, self-validating lat/lon pair. Same semantics as the backend.
 */
public record Coordinates(double latitude, double longitude) {

    public Coordinates {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range [-90,90]: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range [-180,180]: " + longitude);
        }
    }
}
