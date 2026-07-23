package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Outbound persistence adapter: reads raw GTFS rows over JPA and maps them to the
 * loader port's transport records. Read-only.
 */
@Component
public class JpaGtfsLoaderAdapter implements GtfsLoaderPort {

    private final RouteJpaRepository routes;
    private final TripJpaRepository trips;
    private final ShapePointJpaRepository shapePoints;
    private final FeedInfoJpaRepository feedInfo;

    public JpaGtfsLoaderAdapter(RouteJpaRepository routes, TripJpaRepository trips,
                                ShapePointJpaRepository shapePoints,
                                FeedInfoJpaRepository feedInfo) {
        this.routes = routes;
        this.trips = trips;
        this.shapePoints = shapePoints;
        this.feedInfo = feedInfo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawRoute> loadRoutes() {
        return routes.findAll().stream()
                .map(r -> new RawRoute(r.getRouteId(), r.getRouteShortName(),
                        r.getRouteLongName(), r.getRouteType(), r.getAgencyId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawTrip> loadTrips() {
        return trips.findAllWithShape().stream()
                .map(t -> new RawTrip(t.getRouteId(), t.getShapeId(),
                        t.getDirectionId(), t.getTripHeadsign()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawShapePoint> loadShapePoints() {
        return shapePoints.findAll().stream()
                .map(p -> new RawShapePoint(p.getShapeId(), p.getShapePtSequence(),
                        p.getShapePtLat(), p.getShapePtLon()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RawFeedInfo> loadFeedInfo() {
        return feedInfo.findAll().stream()
                .findFirst()
                .map(fi -> new RawFeedInfo(
                        fi.getFeedVersion(),
                        fi.getFeedPublisherName(),
                        fi.getFeedStartDate() == null ? null
                                : fi.getFeedStartDate().atTime(LocalTime.MIDNIGHT)
                                .toInstant(ZoneOffset.UTC)));
    }
}
