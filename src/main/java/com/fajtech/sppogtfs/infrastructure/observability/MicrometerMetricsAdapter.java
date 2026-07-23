package com.fajtech.sppogtfs.infrastructure.observability;

import com.fajtech.sppogtfs.application.port.out.MetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer implementation of the metrics port (§7).
 * Exposes: gtfs_lines_indexed, gtfs_shapes_indexed,
 * gtfs_resolve_total{result=hit|relaxed|unresolved}, gtfs_reload_seconds.
 */
@Component
public class MicrometerMetricsAdapter implements MetricsPort {

    private final AtomicLong linesIndexed = new AtomicLong();
    private final AtomicLong shapesIndexed = new AtomicLong();

    private final Counter resolveHit;
    private final Counter resolveRelaxed;
    private final Counter resolveUnresolved;
    private final Timer reloadTimer;

    public MicrometerMetricsAdapter(MeterRegistry registry) {
        registry.gauge("gtfs_lines_indexed", linesIndexed);
        registry.gauge("gtfs_shapes_indexed", shapesIndexed);
        this.resolveHit = Counter.builder("gtfs_resolve_total").tag("result", "hit").register(registry);
        this.resolveRelaxed = Counter.builder("gtfs_resolve_total").tag("result", "relaxed").register(registry);
        this.resolveUnresolved = Counter.builder("gtfs_resolve_total").tag("result", "unresolved").register(registry);
        this.reloadTimer = Timer.builder("gtfs_reload_seconds").register(registry);
    }

    @Override
    public void recordResolve(ResolveResult result) {
        switch (result) {
            case HIT -> resolveHit.increment();
            case RELAXED -> resolveRelaxed.increment();
            case UNRESOLVED -> resolveUnresolved.increment();
        }
    }

    @Override
    public void updateIndexGauges(long lines, long shapes) {
        linesIndexed.set(lines);
        shapesIndexed.set(shapes);
    }

    @Override
    public void recordReloadSeconds(double seconds) {
        reloadTimer.record(Duration.ofNanos((long) (seconds * 1_000_000_000L)));
    }
}
