package com.fajtech.sppogtfs.application.port.out;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: loads raw GTFS rows from the source database. The application layer
 * builds the in-memory index from these; the port stays free of domain shaping so the
 * persistence adapter can be a thin, testable mapping over the DB.
 */
public interface GtfsLoaderPort {

    /** All routes in the feed (unfiltered — the index applies SPPO filters). */
    List<RawRoute> loadRoutes();

    /** All trips carrying a non-null shape_id. */
    List<RawTrip> loadTrips();

    /** All shape points, unordered (the index sorts by sequence). */
    List<RawShapePoint> loadShapePoints();

    /** Optional GTFS {@code feed_info} row, if the feed provides one. */
    Optional<RawFeedInfo> loadFeedInfo();

    record RawRoute(String routeId, String routeShortName, String routeLongName,
                    Integer routeType, String agencyId) {
    }

    record RawTrip(String routeId, String shapeId, Integer directionId, String headsign) {
    }

    record RawShapePoint(String shapeId, int sequence, double latitude, double longitude) {
    }

    record RawFeedInfo(String feedVersion, String publisherName, java.time.Instant startDate) {
    }
}
