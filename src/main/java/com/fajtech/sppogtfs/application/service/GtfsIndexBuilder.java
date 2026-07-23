package com.fajtech.sppogtfs.application.service;

import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.RawRoute;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.RawShapePoint;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort.RawTrip;
import com.fajtech.sppogtfs.domain.Coordinates;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.RouteShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds an immutable {@link GtfsIndex} from raw GTFS rows, applying SPPO filters and
 * the join/dedup rules of §3 and §5.2. Pure: no Spring, no I/O.
 */
public final class GtfsIndexBuilder {

    /** SPPO filtering knobs. */
    public record FilterConfig(Set<Integer> routeTypes, boolean excludeBrt,
                               Set<String> brtAgencyIds, List<String> brtLinePrefixes) {
        public FilterConfig {
            routeTypes = routeTypes == null ? Set.of() : Set.copyOf(routeTypes);
            brtAgencyIds = brtAgencyIds == null ? Set.of() : Set.copyOf(brtAgencyIds);
            brtLinePrefixes = brtLinePrefixes == null ? List.of() : List.copyOf(brtLinePrefixes);
        }
    }

    public GtfsIndex build(List<RawRoute> routes, List<RawTrip> trips,
                           List<RawShapePoint> shapePoints, FeedVersion feedVersion,
                           FilterConfig filter) {

        // 1. Keep only routes matching the SPPO filter, keyed by route_id.
        Map<String, RawRoute> keptRoutes = new HashMap<>();
        for (RawRoute r : routes) {
            if (accept(r, filter)) {
                keptRoutes.put(r.routeId(), r);
            }
        }

        // 2. Order shape points by sequence → domain coordinates.
        Map<String, List<RawShapePoint>> pointsByShape = new HashMap<>();
        for (RawShapePoint p : shapePoints) {
            pointsByShape.computeIfAbsent(p.shapeId(), k -> new ArrayList<>()).add(p);
        }

        // 3. Trips of kept routes only; group by shape_id and by route_id.
        //    Track (directionId, headsign) frequency per shape to pick a representative trip.
        Map<String, Map<DirHeadsign, Integer>> repVotes = new HashMap<>();
        Map<String, Set<String>> shapesByRoute = new HashMap<>();
        for (RawTrip t : trips) {
            if (t.shapeId() == null || !keptRoutes.containsKey(t.routeId())) {
                continue;
            }
            shapesByRoute.computeIfAbsent(t.routeId(), k -> new LinkedHashSet<>()).add(t.shapeId());
            repVotes.computeIfAbsent(t.shapeId(), k -> new HashMap<>())
                    .merge(new DirHeadsign(t.directionId(), t.headsign()), 1, Integer::sum);
        }

        // 4. Build one RouteShape per distinct shape_id (dedup by shape_id).
        Map<String, RouteShape> byShapeId = new HashMap<>();
        for (Map.Entry<String, Map<DirHeadsign, Integer>> e : repVotes.entrySet()) {
            String shapeId = e.getKey();
            List<RawShapePoint> pts = pointsByShape.get(shapeId);
            if (pts == null || pts.isEmpty()) {
                continue; // trip references a shape with no geometry; skip
            }
            pts.sort(Comparator.comparingInt(RawShapePoint::sequence));
            List<Coordinates> coords = new ArrayList<>(pts.size());
            for (RawShapePoint p : pts) {
                coords.add(new Coordinates(p.latitude(), p.longitude()));
            }
            DirHeadsign rep = representative(e.getValue());
            byShapeId.put(shapeId, RouteShape.of(shapeId, rep.directionId(), rep.headsign(), coords));
        }

        // 5. Assemble line entries: normalized short_name → distinct shapes.
        //    Merge routes that normalize to the same code.
        Map<String, GtfsIndex.LineEntry> byExact = new HashMap<>();
        Map<String, GtfsIndex.LineEntry> byNumeric = new HashMap<>();
        for (RawRoute r : keptRoutes.values()) {
            if (r.routeShortName() == null || r.routeShortName().isBlank()) {
                continue;
            }
            LineCode code = LineCode.of(r.routeShortName());
            List<RouteShape> shapes = new ArrayList<>();
            Set<String> ids = shapesByRoute.getOrDefault(r.routeId(), Set.of());
            for (String sid : ids) {
                RouteShape s = byShapeId.get(sid);
                if (s != null) {
                    shapes.add(s);
                }
            }
            mergeInto(byExact, code.value(), r.routeLongName(), shapes);
            if (code.isNumeric()) {
                mergeInto(byNumeric, code.numericKey(), r.routeLongName(), shapes);
            }
        }

        return new GtfsIndex(byExact, byNumeric, byShapeId, feedVersion);
    }

    private static void mergeInto(Map<String, GtfsIndex.LineEntry> target, String key,
                                  String longName, List<RouteShape> shapes) {
        GtfsIndex.LineEntry existing = target.get(key);
        if (existing == null) {
            target.put(key, new GtfsIndex.LineEntry(key, longName, dedup(shapes)));
            return;
        }
        List<RouteShape> merged = new ArrayList<>(existing.shapes());
        merged.addAll(shapes);
        String name = existing.routeLongName() != null ? existing.routeLongName() : longName;
        target.put(key, new GtfsIndex.LineEntry(key, name, dedup(merged)));
    }

    private static List<RouteShape> dedup(List<RouteShape> shapes) {
        Map<String, RouteShape> unique = new HashMap<>();
        List<RouteShape> ordered = new ArrayList<>();
        for (RouteShape s : shapes) {
            if (unique.putIfAbsent(s.shapeId(), s) == null) {
                ordered.add(s);
            }
        }
        return ordered;
    }

    private boolean accept(RawRoute r, FilterConfig filter) {
        if (r.routeType() == null || !filter.routeTypes().contains(r.routeType())) {
            return false;
        }
        if (filter.excludeBrt() && isBrt(r, filter)) {
            return false;
        }
        return true;
    }

    private boolean isBrt(RawRoute r, FilterConfig filter) {
        if (r.agencyId() != null && filter.brtAgencyIds().contains(r.agencyId())) {
            return true;
        }
        if (r.routeShortName() != null) {
            String upper = r.routeShortName().trim().toUpperCase();
            for (String prefix : filter.brtLinePrefixes()) {
                if (!prefix.isBlank() && upper.startsWith(prefix.toUpperCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static DirHeadsign representative(Map<DirHeadsign, Integer> votes) {
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(new DirHeadsign(null, null));
    }

    private record DirHeadsign(Integer directionId, String headsign) {
    }
}
