package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

/** Raised when a line code cannot be resolved against the feed (→ 404). */
public class LineNotFoundException extends RuntimeException {
    public LineNotFoundException(String lineCode) {
        super("Line not found in feed: " + lineCode);
    }
}
