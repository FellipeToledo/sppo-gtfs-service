package com.fajtech.sppogtfs.application.service;

import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.RouteShape;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable in-memory index: normalized line code → shapes, plus a shape_id lookup and
 * the feed version. Rebuilt off-thread and swapped in atomically (reference swap), so
 * readers never see a half-built index and there is no downtime during reload.
 */
public final class GtfsIndex {

    /** One resolved line: its long name and its distinct shapes. */
    public record LineEntry(String normalizedCode, String routeLongName, List<RouteShape> shapes) {
        public LineEntry {
            shapes = List.copyOf(shapes);
        }
    }

    /** Outcome of a resolution attempt against the index. */
    public record Match(LineEntry entry, boolean relaxed) {
    }

    private final Map<String, LineEntry> byExact;
    private final Map<String, LineEntry> byNumeric;
    private final Map<String, RouteShape> byShapeId;
    private final FeedVersion feedVersion;

    public GtfsIndex(Map<String, LineEntry> byExact,
                     Map<String, LineEntry> byNumeric,
                     Map<String, RouteShape> byShapeId,
                     FeedVersion feedVersion) {
        this.byExact = Map.copyOf(byExact);
        this.byNumeric = Map.copyOf(byNumeric);
        this.byShapeId = Map.copyOf(byShapeId);
        this.feedVersion = feedVersion;
    }

    /** Empty index used before the first successful load. */
    public static GtfsIndex empty(FeedVersion feedVersion) {
        return new GtfsIndex(Map.of(), Map.of(), Map.of(), feedVersion);
    }

    /**
     * Two-step resolution (§5.1): exact normalized match first, then the relaxed numeric
     * fallback (leading zeros stripped). Empty when neither matches.
     */
    public Optional<Match> resolve(LineCode code) {
        LineEntry exact = byExact.get(code.value());
        if (exact != null) {
            return Optional.of(new Match(exact, false));
        }
        if (code.isNumeric()) {
            LineEntry relaxed = byNumeric.get(code.numericKey());
            if (relaxed != null) {
                return Optional.of(new Match(relaxed, true));
            }
        }
        return Optional.empty();
    }

    public Optional<RouteShape> shape(String shapeId) {
        return Optional.ofNullable(byShapeId.get(shapeId));
    }

    public FeedVersion feedVersion() {
        return feedVersion;
    }

    public long linesIndexed() {
        return byExact.size();
    }

    public long shapesIndexed() {
        return byShapeId.size();
    }
}
