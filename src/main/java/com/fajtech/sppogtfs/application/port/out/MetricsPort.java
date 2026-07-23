package com.fajtech.sppogtfs.application.port.out;

/**
 * Outbound port for the minimal business metrics of §7. Implemented over Micrometer in
 * the infrastructure layer, keeping the application free of a metrics framework.
 */
public interface MetricsPort {

    /** Result of a line resolution, for {@code gtfs_resolve_total{result=...}}. */
    enum ResolveResult {
        HIT, RELAXED, UNRESOLVED
    }

    void recordResolve(ResolveResult result);

    /** Publish gauges after a (re)load: indexed line and shape counts. */
    void updateIndexGauges(long linesIndexed, long shapesIndexed);

    /** Record an index reload duration, for {@code gtfs_reload_seconds}. */
    void recordReloadSeconds(double seconds);
}
