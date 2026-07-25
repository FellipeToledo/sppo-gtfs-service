package com.fajtech.sppogtfs.application.service;

import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.PlannedShapeRef;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.ShapeGeometry;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.ShapeSegment;
import com.fajtech.sppogtfs.domain.Coordinates;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.RouteSegment;
import com.fajtech.sppogtfs.domain.RouteShape;
import com.fajtech.sppogtfs.domain.WktLineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds an immutable {@link GtfsIndex} from SMTR planning rows, applying the SPPO
 * {@code modo} filter and the join/dedup rules. Pure: no Spring, no I/O.
 */
public final class GtfsIndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(GtfsIndexBuilder.class);

    /** SPPO filtering knobs. {@code allowedModos} is compared case/space-insensitively. */
    public record FilterConfig(Set<String> allowedModos) {
        public FilterConfig {
            allowedModos = allowedModos == null ? Set.of()
                    : allowedModos.stream().map(GtfsIndexBuilder::norm).collect(Collectors.toSet());
        }

        boolean accepts(String modo) {
            return modo != null && allowedModos.contains(norm(modo));
        }
    }

    public GtfsIndex build(List<PlannedShapeRef> refs, List<ShapeGeometry> geometries,
                           FeedVersion feedVersion, FilterConfig filter) {
        return build(refs, geometries, List.of(), feedVersion, filter);
    }

    public GtfsIndex build(List<PlannedShapeRef> refs, List<ShapeGeometry> geometries,
                           List<ShapeSegment> segments, FeedVersion feedVersion,
                           FilterConfig filter) {

        // 1. shape_id -> WKT (dedup; last wins).
        Map<String, String> wktByShape = new HashMap<>();
        for (ShapeGeometry g : geometries) {
            if (g.shapeId() != null && g.wkt() != null) {
                wktByShape.put(g.shapeId(), g.wkt());
            }
        }

        // 1b. shape_id -> SMTR segments, in arrival order. An empty list is legitimate
        //     (feed with no segmentation for that shape): no exclusions, as before.
        Map<String, List<RouteSegment>> segmentsByShape = new HashMap<>();
        int invalidSegments = 0;
        for (ShapeSegment seg : segments) {
            if (seg.shapeId() == null || seg.wkt() == null) {
                invalidSegments++;
                continue;
            }
            try {
                segmentsByShape.computeIfAbsent(seg.shapeId(), k -> new ArrayList<>())
                        .add(new RouteSegment(seg.segmentId(), WktLineString.parse(seg.wkt()),
                                seg.lengthMeters(), seg.disregarded(), seg.tunnel(),
                                seg.smallSegment(), seg.damagedBuffer()));
            } catch (RuntimeException ex) {
                invalidSegments++;
                log.warn("failed to parse segment {} of shape {}: {}",
                        seg.segmentId(), seg.shapeId(), ex.getMessage());
            }
        }
        if (invalidSegments > 0) {
            log.warn("skipped {} of {} segments (unparseable)", invalidSegments, segments.size());
        }

        // 2. Keep refs whose modo is allowed; vote representative (sentido,evento) per shape_id;
        //    collect shape_ids per normalized servico.
        Map<String, Map<SentidoEvento, Integer>> repVotes = new HashMap<>();
        Map<String, LinkedHashSet<String>> shapesByLine = new HashMap<>();
        Map<String, String> longNameByLine = new HashMap<>(); // no long name in feed; reserved
        for (PlannedShapeRef r : refs) {
            if (r.shapeId() == null || r.servico() == null || !filter.accepts(r.modo())) {
                continue;
            }
            LineCode code = LineCode.of(r.servico());
            shapesByLine.computeIfAbsent(code.value(), k -> new LinkedHashSet<>()).add(r.shapeId());
            repVotes.computeIfAbsent(r.shapeId(), k -> new HashMap<>())
                    .merge(new SentidoEvento(r.sentido(), r.evento()), 1, Integer::sum);
        }

        // 3. Build one RouteShape per distinct shape_id that has geometry.
        Map<String, RouteShape> byShapeId = new HashMap<>();
        for (var e : repVotes.entrySet()) {
            String shapeId = e.getKey();
            String wkt = wktByShape.get(shapeId);
            if (wkt == null) {
                log.debug("shape {} referenced by a trip has no geometry; skipping", shapeId);
                continue;
            }
            try {
                List<Coordinates> pts = WktLineString.parse(wkt);
                SentidoEvento rep = representative(e.getValue());
                byShapeId.put(shapeId, RouteShape
                        .of(shapeId, directionId(rep.sentido()), rep.sentido(), rep.evento(), pts)
                        .withSegments(segmentsByShape.getOrDefault(shapeId, List.of())));
            } catch (RuntimeException ex) {
                log.warn("failed to parse geometry for shape {}: {}", shapeId, ex.getMessage());
            }
        }

        // 4. Assemble line entries: normalized servico -> distinct shapes.
        Map<String, GtfsIndex.LineEntry> byExact = new HashMap<>();
        Map<String, GtfsIndex.LineEntry> byNumeric = new HashMap<>();
        for (var e : shapesByLine.entrySet()) {
            String normalized = e.getKey();
            List<RouteShape> shapes = new ArrayList<>();
            for (String sid : e.getValue()) {
                RouteShape s = byShapeId.get(sid);
                if (s != null) {
                    shapes.add(s);
                }
            }
            GtfsIndex.LineEntry entry =
                    new GtfsIndex.LineEntry(normalized, longNameByLine.get(normalized), shapes);
            byExact.put(normalized, entry);
            LineCode code = LineCode.of(normalized);
            if (code.isNumeric()) {
                byNumeric.merge(code.numericKey(), entry, GtfsIndexBuilder::mergeEntries);
            }
        }

        return new GtfsIndex(byExact, byNumeric, byShapeId, feedVersion);
    }

    private static GtfsIndex.LineEntry mergeEntries(GtfsIndex.LineEntry a, GtfsIndex.LineEntry b) {
        Map<String, RouteShape> unique = new HashMap<>();
        List<RouteShape> ordered = new ArrayList<>();
        for (RouteShape s : a.shapes()) {
            if (unique.putIfAbsent(s.shapeId(), s) == null) {
                ordered.add(s);
            }
        }
        for (RouteShape s : b.shapes()) {
            if (unique.putIfAbsent(s.shapeId(), s) == null) {
                ordered.add(s);
            }
        }
        String name = a.routeLongName() != null ? a.routeLongName() : b.routeLongName();
        return new GtfsIndex.LineEntry(a.normalizedCode(), name, ordered);
    }

    /** Map SMTR {@code sentido} to a GTFS-style 0/1 when possible, else null. */
    static Integer directionId(String sentido) {
        if (sentido == null) {
            return null;
        }
        return switch (sentido.trim().toUpperCase(Locale.ROOT)) {
            case "I", "IDA", "0" -> 0;
            case "V", "VOLTA", "1" -> 1;
            default -> null;
        };
    }

    private static SentidoEvento representative(Map<SentidoEvento, Integer> votes) {
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(new SentidoEvento(null, null));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private record SentidoEvento(String sentido, String evento) {
    }
}
