package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

/** Raised when a shape_id is not present in the index (→ 404). */
public class ShapeNotFoundException extends RuntimeException {
    public ShapeNotFoundException(String shapeId) {
        super("Shape not found in feed: " + shapeId);
    }
}
