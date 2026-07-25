package com.fajtech.sppogtfs.application.service;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.BatchResult;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.PlannedShapeRef;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.ShapeGeometry;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.ShapeSegment;
import com.fajtech.sppogtfs.application.port.out.MetricsPort;
import com.fajtech.sppogtfs.domain.LineItinerary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsQueryServiceTest {

    private GtfsQueryService service;

    @BeforeEach
    void setUp() {
        List<GtfsLoaderPort.PlannedShapeRef> refs = List.of(
                // line 100: ida + volta (regular), plus an alternate ida trajectory
                new GtfsLoaderPort.PlannedShapeRef("100", "R100", "s100i", "I", null, "Ônibus", false),
                new GtfsLoaderPort.PlannedShapeRef("100", "R100", "s100i", "I", null, "Ônibus", false),
                new GtfsLoaderPort.PlannedShapeRef("100", "R100", "s100v", "V", null, "Ônibus", false),
                new GtfsLoaderPort.PlannedShapeRef("100", "R100", "s100alt", "I", "Desvio", "Ônibus", true),
                // line 200: referenced but its shape has no geometry -> no_shapes
                new GtfsLoaderPort.PlannedShapeRef("200", "R200", "s200", "I", null, "Ônibus", false),
                // BRT service: filtered out entirely by modo
                new GtfsLoaderPort.PlannedShapeRef("LECD05", "RBRT", "sbrt", "I", null, "BRT", false));

        List<GtfsLoaderPort.ShapeGeometry> geoms = List.of(
                new GtfsLoaderPort.ShapeGeometry("s100i",
                        "LINESTRING(-43.20 -22.90, -43.20 -22.91, -43.19 -22.92)"),
                new GtfsLoaderPort.ShapeGeometry("s100v",
                        "LINESTRING(-43.19 -22.92, -43.20 -22.91, -43.20 -22.90)"),
                new GtfsLoaderPort.ShapeGeometry("s100alt",
                        "LINESTRING(-43.20 -22.90, -43.195 -22.908, -43.19 -22.92)"),
                new GtfsLoaderPort.ShapeGeometry("sbrt",
                        "LINESTRING(-43.40 -22.95, -43.41 -22.96)"));

        GtfsLoaderPort loader = new FakeLoader(refs, geoms);
        var filter = new GtfsIndexBuilder.FilterConfig(Set.of("Ônibus"));
        var settings = new GtfsQueryService.Settings(filter, "test");
        service = new GtfsQueryService(loader, new GtfsIndexBuilder(), new NoopMetrics(), settings);
        service.reload();
    }

    @Test
    void lineReturnsDistinctShapesIncludingAlternate() {
        LineItinerary it = service.findLineShapes("100", 0).orElseThrow();
        assertThat(it.shapes()).hasSize(3);
        assertThat(it.resolution()).isEqualTo(LineItinerary.Resolution.EXACT);
        assertThat(it.shapes().stream().map(s -> s.shapeId()))
                .containsExactlyInAnyOrder("s100i", "s100v", "s100alt");
    }

    @Test
    void sentidoMapsToDirectionIdAndEventoIsCarried() {
        LineItinerary it = service.findLineShapes("100", 0).orElseThrow();
        var ida = it.shapes().stream().filter(s -> s.shapeId().equals("s100i")).findFirst().orElseThrow();
        var volta = it.shapes().stream().filter(s -> s.shapeId().equals("s100v")).findFirst().orElseThrow();
        var alt = it.shapes().stream().filter(s -> s.shapeId().equals("s100alt")).findFirst().orElseThrow();
        assertThat(ida.directionId()).isEqualTo(0);
        assertThat(ida.sentido()).isEqualTo("I");
        assertThat(volta.directionId()).isEqualTo(1);
        assertThat(alt.evento()).isEqualTo("Desvio");
    }

    @Test
    void geometryIsParsedFromWktInOrder() {
        LineItinerary it = service.findLineShapes("100", 0).orElseThrow();
        var ida = it.shapes().stream().filter(s -> s.shapeId().equals("s100i")).findFirst().orElseThrow();
        assertThat(ida.pointCount()).isEqualTo(3);
        assertThat(ida.points().get(0).latitude()).isEqualTo(-22.90);
        assertThat(ida.points().get(0).longitude()).isEqualTo(-43.20);
    }

    @Test
    void relaxedFallbackMatchesLeadingZeros() {
        LineItinerary it = service.findLineShapes("0100", 0).orElseThrow();
        assertThat(it.resolution()).isEqualTo(LineItinerary.Resolution.RELAXED);
        assertThat(it.shapes()).hasSize(3);
    }

    @Test
    void existingLineWithoutGeometryResolvesAsNoShapes() {
        LineItinerary it = service.findLineShapes("200", 0).orElseThrow();
        assertThat(it.shapes()).isEmpty();
        assertThat(it.resolution()).isEqualTo(LineItinerary.Resolution.NO_SHAPES);
    }

    @Test
    void unknownLineIsUnresolved() {
        assertThat(service.findLineShapes("999", 0)).isEmpty();
    }

    @Test
    void brtServiceIsFilteredOut() {
        assertThat(service.findLineShapes("LECD05", 0)).isEmpty();
    }

    @Test
    void batchSplitsResolvedAndUnresolved() {
        BatchResult result = service.findLineShapesBatch(List.of("100", "0100", "999"), 0);
        assertThat(result.items()).containsKeys("100", "0100");
        assertThat(result.unresolved()).containsExactly("999");
    }

    private static GtfsQueryService newService(GtfsLoaderPort loader) {
        var filter = new GtfsIndexBuilder.FilterConfig(Set.of("Ônibus"));
        var svc = new GtfsQueryService(loader, new GtfsIndexBuilder(), new NoopMetrics(),
                new GtfsQueryService.Settings(filter, "test"));
        svc.reload();
        return svc;
    }

    @Test
    void shouldAttachSmtrSegmentsToTheShape() {
        // Segments arrive from SMTR with the flags as published; the index only attaches them.
        var loader = new FakeLoader(
                List.of(new PlannedShapeRef("100", null, "s100i", "I", null, "Ônibus", false)),
                List.of(new ShapeGeometry("s100i",
                        "LINESTRING(-43.2000 -22.9000, -43.2000 -22.9100, -43.1900 -22.9200)")),
                List.of(
                        new ShapeSegment("s100i", "seg-1",
                                "LINESTRING(-43.2000 -22.9000, -43.2000 -22.9100)",
                                1113.0, false, false, false, false),
                        new ShapeSegment("s100i", "seg-2",
                                "LINESTRING(-43.2000 -22.9100, -43.1900 -22.9200)",
                                1500.0, true, true, false, false)));
        var svc = newService(loader);

        var shapes = svc.findLineShapes("100", 0).orElseThrow().shapes();

        assertThat(shapes).singleElement().satisfies(shape -> {
            assertThat(shape.segments()).hasSize(2);
            assertThat(shape.disregardedSegmentCount()).isEqualTo(1);
            assertThat(shape.segments().get(1).disregarded()).isTrue();
            assertThat(shape.segments().get(1).tunnel()).isTrue();
            assertThat(shape.segments().get(0).participatesInValidation()).isTrue();
        });
    }

    @Test
    void shouldSurviveAnUnparseableSegmentWithoutLosingTheShape() {
        var loader = new FakeLoader(
                List.of(new PlannedShapeRef("100", null, "s100i", "I", null, "Ônibus", false)),
                List.of(new ShapeGeometry("s100i",
                        "LINESTRING(-43.2000 -22.9000, -43.1900 -22.9200)")),
                List.of(new ShapeSegment("s100i", "ruim", "NAO_E_WKT",
                        10.0, true, false, false, false)));
        var svc = newService(loader);

        var shapes = svc.findLineShapes("100", 0).orElseThrow().shapes();

        assertThat(shapes).singleElement().satisfies(shape ->
                assertThat(shape.segments()).isEmpty());
    }

    @Test
    void feedSnapshotReflectsLoaderFeedAndCounts() {
        var snap = service.feedSnapshot();
        assertThat(snap.feedVersion().id()).isEqualTo("FV-1");
        assertThat(snap.linesIndexed()).isEqualTo(2); // 100 and 200 (BRT excluded)
        assertThat(snap.shapesIndexed()).isEqualTo(3); // s100i, s100v, s100alt (sbrt filtered, s200 no geom)
    }

    private record FakeLoader(List<PlannedShapeRef> refs, List<ShapeGeometry> geoms,
                             List<ShapeSegment> segments) implements GtfsLoaderPort {

        FakeLoader(List<PlannedShapeRef> refs, List<ShapeGeometry> geoms) {
            this(refs, geoms, List.of());
        }

        @Override
        public FeedInfo currentFeed() {
            return new FeedInfo("FV-1", Instant.parse("2026-07-19T00:00:00Z"));
        }

        @Override
        public List<PlannedShapeRef> loadPlannedShapeRefs() {
            return refs;
        }

        @Override
        public List<ShapeGeometry> loadShapeGeometries() {
            return geoms;
        }

        @Override
        public List<ShapeSegment> loadShapeSegments() {
            return segments;
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
