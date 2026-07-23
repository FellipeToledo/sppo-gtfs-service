package com.fajtech.sppogtfs.domain;

import java.util.List;

/**
 * Aggregated response for one line: all distinct shapes plus feed version.
 *
 * <p>{@code resolution} distinguishes how the line was resolved:
 * <ul>
 *   <li>{@code EXACT} — matched {@code route_short_name} exactly.</li>
 *   <li>{@code RELAXED} — matched via the leading-zeros numeric fallback.</li>
 *   <li>{@code NO_SHAPES} — line exists but has no shapes (backend treats as unresolved,
 *       does not drop classification).</li>
 * </ul>
 */
public record LineItinerary(
        LineCode line,
        String routeLongName,
        List<RouteShape> shapes,
        FeedVersion feedVersion,
        Resolution resolution) {

    public enum Resolution {
        EXACT, RELAXED, NO_SHAPES
    }

    public LineItinerary {
        shapes = shapes == null ? List.of() : List.copyOf(shapes);
    }
}
