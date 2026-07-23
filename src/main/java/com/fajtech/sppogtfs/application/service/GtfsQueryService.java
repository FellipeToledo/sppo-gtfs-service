package com.fajtech.sppogtfs.application.service;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.RawFeedInfo;
import com.fajtech.sppogtfs.application.port.out.MetricsPort;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.LineItinerary;
import com.fajtech.sppogtfs.domain.RouteShape;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core read service: holds the in-memory index, resolves lines, and rebuilds the index
 * atomically on reload. Plain class (framework-free); wired by the infrastructure layer.
 */
public class GtfsQueryService implements GtfsQueryPort, ReloadIndexPort {

    /** Configuration the service needs, decoupled from any Spring properties type. */
    public record Settings(GtfsIndexBuilder.FilterConfig filter, String feedSource,
                           String fallbackFeedId) {
    }

    private static final DateTimeFormatter MONTH_ID =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final GtfsLoaderPort loader;
    private final GtfsIndexBuilder builder;
    private final MetricsPort metrics;
    private final Settings settings;
    private final AtomicReference<GtfsIndex> indexRef;

    public GtfsQueryService(GtfsLoaderPort loader, GtfsIndexBuilder builder,
                            MetricsPort metrics, Settings settings) {
        this.loader = loader;
        this.builder = builder;
        this.metrics = metrics;
        this.settings = settings;
        this.indexRef = new AtomicReference<>(GtfsIndex.empty(bootstrapFeedVersion()));
    }

    // --- Queries -------------------------------------------------------------

    @Override
    public Optional<LineItinerary> findLineShapes(String rawLine, double simplifyMeters) {
        GtfsIndex index = indexRef.get();
        LineCode code = LineCode.of(rawLine);
        Optional<GtfsIndex.Match> match = index.resolve(code);
        if (match.isEmpty()) {
            metrics.recordResolve(MetricsPort.ResolveResult.UNRESOLVED);
            return Optional.empty();
        }
        GtfsIndex.Match m = match.get();
        metrics.recordResolve(m.relaxed()
                ? MetricsPort.ResolveResult.RELAXED
                : MetricsPort.ResolveResult.HIT);

        List<RouteShape> shapes = applySimplify(m.entry().shapes(), simplifyMeters);
        LineItinerary.Resolution resolution = shapes.isEmpty()
                ? LineItinerary.Resolution.NO_SHAPES
                : (m.relaxed() ? LineItinerary.Resolution.RELAXED : LineItinerary.Resolution.EXACT);
        return Optional.of(new LineItinerary(
                LineCode.of(m.entry().normalizedCode()),
                m.entry().routeLongName(),
                shapes,
                index.feedVersion(),
                resolution));
    }

    @Override
    public BatchResult findLineShapesBatch(List<String> rawLines, double simplifyMeters) {
        Map<String, LineItinerary> items = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        for (String raw : rawLines) {
            Optional<LineItinerary> it = findLineShapes(raw, simplifyMeters);
            if (it.isPresent()) {
                items.put(raw, it.get());
            } else {
                unresolved.add(raw);
            }
        }
        return new BatchResult(items, unresolved, indexRef.get().feedVersion());
    }

    @Override
    public Optional<RouteShape> findShape(String shapeId) {
        return indexRef.get().shape(shapeId);
    }

    @Override
    public Optional<LineMetadata> findLineMetadata(String rawLine) {
        GtfsIndex index = indexRef.get();
        return index.resolve(LineCode.of(rawLine)).map(m -> {
            TreeSet<Integer> directions = new TreeSet<>();
            for (RouteShape s : m.entry().shapes()) {
                if (s.directionId() != null) {
                    directions.add(s.directionId());
                }
            }
            return new LineMetadata(
                    m.entry().normalizedCode(),
                    m.entry().routeLongName(),
                    new ArrayList<>(directions),
                    m.entry().shapes().size(),
                    index.feedVersion());
        });
    }

    @Override
    public FeedVersion feedVersion() {
        return indexRef.get().feedVersion();
    }

    @Override
    public FeedSnapshot feedSnapshot() {
        GtfsIndex index = indexRef.get();
        return new FeedSnapshot(index.feedVersion(), index.linesIndexed(), index.shapesIndexed());
    }

    // --- Reload --------------------------------------------------------------

    @Override
    public synchronized ReloadResult reload() {
        long start = System.nanoTime();
        FeedVersion feedVersion = resolveFeedVersion();
        GtfsIndex fresh = builder.build(
                loader.loadRoutes(),
                loader.loadTrips(),
                loader.loadShapePoints(),
                feedVersion,
                settings.filter());
        indexRef.set(fresh); // atomic reference swap — no downtime
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        metrics.updateIndexGauges(fresh.linesIndexed(), fresh.shapesIndexed());
        metrics.recordReloadSeconds(seconds);
        return new ReloadResult(feedVersion.id(), fresh.linesIndexed(), fresh.shapesIndexed(), seconds);
    }

    // --- Helpers -------------------------------------------------------------

    private List<RouteShape> applySimplify(List<RouteShape> shapes, double simplifyMeters) {
        if (simplifyMeters <= 0) {
            return shapes;
        }
        List<RouteShape> out = new ArrayList<>(shapes.size());
        for (RouteShape s : shapes) {
            out.add(s.simplified(simplifyMeters));
        }
        return out;
    }

    private FeedVersion resolveFeedVersion() {
        Optional<RawFeedInfo> info = loader.loadFeedInfo();
        if (info.isPresent() && info.get().feedVersion() != null
                && !info.get().feedVersion().isBlank()) {
            RawFeedInfo fi = info.get();
            Instant publishedAt = fi.startDate() != null ? fi.startDate() : Instant.now();
            String source = fi.publisherName() != null ? fi.publisherName() : settings.feedSource();
            return new FeedVersion(fi.feedVersion(), publishedAt, source);
        }
        return bootstrapFeedVersion();
    }

    private FeedVersion bootstrapFeedVersion() {
        String id = settings.fallbackFeedId() != null && !settings.fallbackFeedId().isBlank()
                ? settings.fallbackFeedId()
                : MONTH_ID.format(Instant.now());
        return new FeedVersion(id, Instant.now(), settings.feedSource());
    }
}
