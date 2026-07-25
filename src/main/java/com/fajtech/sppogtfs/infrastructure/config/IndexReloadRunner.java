package com.fajtech.sppogtfs.infrastructure.config;

import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives index (re)loads: once on startup (if enabled) and on the configured daily cron.
 * The reload itself is atomic in the service, so scheduled runs never cause downtime.
 *
 * <h2>Why {@code ApplicationStartedEvent} and not {@code ApplicationReadyEvent}</h2>
 *
 * Spring Boot flips readiness to {@code ACCEPTING_TRAFFIC} when it publishes
 * {@code ApplicationReadyEvent} — which happens <b>after</b> this event. Loading on
 * <em>started</em> therefore guarantees that {@code /actuator/health/readiness} only
 * reports UP once the index is built, and the container healthcheck (and any
 * {@code depends_on: service_healthy}) can be trusted.
 *
 * <p>Loading on <em>ready</em> instead created a real race: the HTTP port is already open
 * during startup, so a consumer that starts alongside this service resolved every line
 * against an <b>empty index</b> for the ~20 s of the BigQuery load, and cached those empty
 * answers. Measured in the backend on 2026-07-25: 285 empty resolutions in the first
 * minute. **Do not move this back to {@code ApplicationReadyEvent}.**
 */
@Component
public class IndexReloadRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexReloadRunner.class);

    private final ReloadIndexPort reload;
    private final GtfsProperties props;

    public IndexReloadRunner(ReloadIndexPort reload, GtfsProperties props) {
        this.reload = reload;
        this.props = props;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onStartup() {
        if (!props.getReload().isOnStartup()) {
            log.info("Startup index load disabled (sppo.gtfs.reload.on-startup=false)");
            return;
        }
        safeReload("startup");
    }

    @Scheduled(cron = "${sppo.gtfs.reload.cron:0 0 4 * * *}", zone = "UTC")
    public void scheduledReload() {
        safeReload("scheduled");
    }

    private void safeReload(String trigger) {
        try {
            var result = reload.reload();
            log.info("GTFS index reloaded [{}] feedVersion={} lines={} shapes={} in {}s",
                    trigger, result.feedVersionId(), result.linesIndexed(),
                    result.shapesIndexed(), String.format("%.2f", result.durationSeconds()));
        } catch (Exception e) {
            log.error("GTFS index reload [{}] failed: {}", trigger, e.getMessage(), e);
        }
    }
}
