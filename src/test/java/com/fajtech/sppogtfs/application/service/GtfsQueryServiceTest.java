package com.fajtech.sppogtfs.application.service;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.BatchResult;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import com.fajtech.sppogtfs.application.port.out.MetricsPort;
import com.fajtech.sppogtfs.domain.LineItinerary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsQueryServiceTest {

    private GtfsQueryService service;

    @BeforeEach
    void setUp() {
        List<GtfsLoaderPort.RawRoute> routes = List.of(
                new GtfsLoaderPort.RawRoute("R100", "100", "Rodoviária <-> Praça XV", 3, "SPPO"),
                new GtfsLoaderPort.RawRoute("R200", "200", "Line with no shapes", 3, "SPPO"),
                new GtfsLoaderPort.RawRoute("RBRT", "BRT1", "Transoeste", 3, "BRT"),
                new GtfsLoaderPort.RawRoute("RTRAIN", "T1", "A train", 2, "SUPERVIA"));

        List<GtfsLoaderPort.RawTrip> trips = List.of(
                new GtfsLoaderPort.RawTrip("R100", "S100_0", 0, "Praça XV"),
                new GtfsLoaderPort.RawTrip("R100", "S100_0", 0, "Praça XV"), // most frequent
                new GtfsLoaderPort.RawTrip("R100", "S100_1", 1, "Rodoviária"),
                new GtfsLoaderPort.RawTrip("R200", null, 0, "Nowhere"),      // no shape
                new GtfsLoaderPort.RawTrip("RBRT", "SBRT", 0, "BRT"),
                new GtfsLoaderPort.RawTrip("RTRAIN", "STRAIN", 0, "Train"));

        List<GtfsLoaderPort.RawShapePoint> points = List.of(
                new GtfsLoaderPort.RawShapePoint("S100_0", 2, -22.91, -43.20),
                new GtfsLoaderPort.RawShapePoint("S100_0", 1, -22.90, -43.20), // out of order
                new GtfsLoaderPort.RawShapePoint("S100_1", 1, -22.91, -43.20),
                new GtfsLoaderPort.RawShapePoint("S100_1", 2, -22.90, -43.20),
                new GtfsLoaderPort.RawShapePoint("SBRT", 1, -22.95, -43.40),
                new GtfsLoaderPort.RawShapePoint("SBRT", 2, -22.96, -43.41),
                new GtfsLoaderPort.RawShapePoint("STRAIN", 1, -22.80, -43.10),
                new GtfsLoaderPort.RawShapePoint("STRAIN", 2, -22.81, -43.11));

        GtfsLoaderPort loader = new FakeLoader(routes, trips, points);
        var filter = new GtfsIndexBuilder.FilterConfig(Set.of(3), true, Set.of("BRT"), List.of());
        var settings = new GtfsQueryService.Settings(filter, "test", "2026-07");
        service = new GtfsQueryService(loader, new GtfsIndexBuilder(),
                new NoopMetrics(), settings);
        service.reload();
    }

    @Test
    void lineWithMultipleShapesReturnsDistinctDedupedShapes() {
        LineItinerary it = service.findLineShapes("100", 0).orElseThrow();
        assertThat(it.shapes()).hasSize(2);
        assertThat(it.resolution()).isEqualTo(LineItinerary.Resolution.EXACT);
        assertThat(it.shapes().stream().map(s -> s.shapeId()))
                .containsExactlyInAnyOrder("S100_0", "S100_1");
    }

    @Test
    void representativeTripDrivesDirectionAndHeadsign() {
        LineItinerary it = service.findLineShapes("100", 0).orElseThrow();
        var s0 = it.shapes().stream().filter(s -> s.shapeId().equals("S100_0")).findFirst().orElseThrow();
        assertThat(s0.directionId()).isEqualTo(0);
        assertThat(s0.headsign()).isEqualTo("Praça XV");
    }

    @Test
    void pointsAreOrderedBySequence() {
        LineItinerary it = service.findLineShapes("100", 0).orElseThrow();
        var s0 = it.shapes().stream().filter(s -> s.shapeId().equals("S100_0")).findFirst().orElseThrow();
        assertThat(s0.points().get(0).latitude()).isEqualTo(-22.90); // seq 1 first
    }

    @Test
    void relaxedFallbackMatchesLeadingZeros() {
        LineItinerary it = service.findLineShapes("0100", 0).orElseThrow();
        assertThat(it.resolution()).isEqualTo(LineItinerary.Resolution.RELAXED);
        assertThat(it.shapes()).hasSize(2);
    }

    @Test
    void existingLineWithoutShapesResolvesAsNoShapes() {
        LineItinerary it = service.findLineShapes("200", 0).orElseThrow();
        assertThat(it.shapes()).isEmpty();
        assertThat(it.resolution()).isEqualTo(LineItinerary.Resolution.NO_SHAPES);
    }

    @Test
    void unknownLineIsUnresolved() {
        assertThat(service.findLineShapes("999", 0)).isEmpty();
    }

    @Test
    void brtAndNonBusRoutesAreFilteredOut() {
        assertThat(service.findLineShapes("BRT1", 0)).isEmpty();
        assertThat(service.findLineShapes("T1", 0)).isEmpty();
    }

    @Test
    void batchSplitsResolvedAndUnresolved() {
        BatchResult result = service.findLineShapesBatch(List.of("100", "0100", "999"), 0);
        assertThat(result.items()).containsKeys("100", "0100");
        assertThat(result.unresolved()).containsExactly("999");
    }

    @Test
    void feedSnapshotReportsCounts() {
        var snap = service.feedSnapshot();
        assertThat(snap.feedVersion().id()).isEqualTo("2026-07");
        assertThat(snap.routesIndexed()).isEqualTo(2); // 100 and 200 (buses, non-BRT)
        assertThat(snap.shapesIndexed()).isEqualTo(2); // S100_0, S100_1
    }

    private record FakeLoader(List<RawRoute> routes, List<RawTrip> trips,
                              List<RawShapePoint> points) implements GtfsLoaderPort {
        @Override
        public List<RawRoute> loadRoutes() {
            return routes;
        }

        @Override
        public List<RawTrip> loadTrips() {
            return trips;
        }

        @Override
        public List<RawShapePoint> loadShapePoints() {
            return points;
        }

        @Override
        public Optional<RawFeedInfo> loadFeedInfo() {
            return Optional.empty();
        }
    }

    private static final class NoopMetrics implements MetricsPort {
        @Override
        public void recordResolve(ResolveResult result) {
        }

        @Override
        public void updateIndexGauges(long linesIndexed, long shapesIndexed) {
        }

        @Override
        public void recordReloadSeconds(double seconds) {
        }
    }
}
