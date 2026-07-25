package com.fajtech.sppogtfs.infrastructure.adapter.out.demo;

import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * In-memory loader for the {@code demo} profile — lets the full app run (index build →
 * REST) without BigQuery credentials. Data is fictional but shaped like the real SMTR feed
 * (servico / sentido / evento / modo, geometry as WKT LINESTRING). NOT for production.
 */
@Component
@Profile("demo")
public class DemoGtfsLoaderAdapter implements GtfsLoaderPort {

    @Override
    public FeedInfo currentFeed() {
        return new FeedInfo("2026-07-18 13:00:00-03:00", Instant.parse("2026-07-19T00:00:00Z"));
    }

    @Override
    public List<PlannedShapeRef> loadPlannedShapeRefs() {
        return List.of(
                // line 100: ida (regular) + volta (regular) + an alternate ida trajectory
                new PlannedShapeRef("100", "R100AAA0A", "s100i", "I", null, "Ônibus", false),
                new PlannedShapeRef("100", "R100AAA0A", "s100v", "V", null, "Ônibus", false),
                new PlannedShapeRef("100", "R100AAA0A", "s100i_alt", "I", "Desvio", "Ônibus", true),
                // line 2110: single ida
                new PlannedShapeRef("2110", "E2110AAA0A", "s2110i", "I", null, "Ônibus", false),
                // a BRT service that must be filtered out (modo != Ônibus)
                new PlannedShapeRef("LECD05", "BRTLECD05", "sbrt", "I", null, "BRT", false));
    }

    @Override
    public List<ShapeGeometry> loadShapeGeometries() {
        return List.of(
                new ShapeGeometry("s100i",
                        "LINESTRING(-43.2000 -22.9000, -43.2000 -22.9100, -43.1900 -22.9200)"),
                new ShapeGeometry("s100v",
                        "LINESTRING(-43.1900 -22.9200, -43.2000 -22.9100, -43.2000 -22.9000)"),
                new ShapeGeometry("s100i_alt",
                        "LINESTRING(-43.2000 -22.9000, -43.1950 -22.9080, -43.1900 -22.9200)"),
                new ShapeGeometry("s2110i",
                        "LINESTRING(-43.31258 -23.0066, -43.31626 -23.00637, -43.32476 -23.00292, "
                                + "-43.33756 -23.00033, -43.34845 -23.00005, -43.36616 -23.00101)"),
                new ShapeGeometry("sbrt",
                        "LINESTRING(-43.4000 -22.9500, -43.4100 -22.9600)"));
    }

    /**
     * Sample segments for one shape only, exercising the exclusion path end to end:
     * {@code s100i} is split in two, the second one disregarded by SMTR. The other shapes
     * come back with no segments, which is the legitimate "no exclusions known" case.
     */
    @Override
    public List<ShapeSegment> loadShapeSegments() {
        return List.of(
                new ShapeSegment("s100i", "s100i-1",
                        "LINESTRING(-43.2000 -22.9000, -43.2000 -22.9100)",
                        1113.0, false, false, false, false),
                new ShapeSegment("s100i", "s100i-2",
                        "LINESTRING(-43.2000 -22.9100, -43.1900 -22.9200)",
                        1500.0, true, true, false, false));
    }
}
