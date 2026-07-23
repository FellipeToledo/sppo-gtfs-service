package com.fajtech.sppogtfs.application.port.in;

import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineItinerary;
import com.fajtech.sppogtfs.domain.RouteShape;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inbound port: read-only GTFS queries consumed by the REST adapter.
 */
public interface GtfsQueryPort {

    /**
     * Resolve a raw GPS line code to its itinerary.
     *
     * @return empty when the line is unresolved (→ 404 on the per-line endpoint,
     * or {@code unresolved} in a batch). A resolved line with no shapes returns a
     * present itinerary whose resolution is {@code NO_SHAPES}.
     */
    Optional<LineItinerary> findLineShapes(String rawLine, double simplifyMeters);

    /** Batch resolve. Unresolved lines are collected separately. */
    BatchResult findLineShapesBatch(List<String> rawLines, double simplifyMeters);

    /** Standalone shape geometry by shape_id. */
    Optional<RouteShape> findShape(String shapeId);

    /** Light metadata for a line (names, available directions, shape count). */
    Optional<LineMetadata> findLineMetadata(String rawLine);

    /** Current feed version. */
    FeedVersion feedVersion();

    /** Feed version plus index counts for the {@code feed/version} endpoint. */
    FeedSnapshot feedSnapshot();

    record BatchResult(Map<String, LineItinerary> items, List<String> unresolved,
                       FeedVersion feedVersion) {
    }

    record LineMetadata(String line, String routeLongName, List<Integer> directions,
                        int shapeCount, FeedVersion feedVersion) {
    }

    record FeedSnapshot(FeedVersion feedVersion, long routesIndexed, long shapesIndexed) {
    }
}
