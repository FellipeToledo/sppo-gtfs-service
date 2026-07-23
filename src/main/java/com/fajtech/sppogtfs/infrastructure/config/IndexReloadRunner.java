package com.fajtech.sppogtfs.infrastructure.config;

import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives index (re)loads: once on startup (if enabled) and on the configured daily cron.
 * The reload itself is atomic in the service, so scheduled runs never cause downtime.
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

    @EventListener(ApplicationReadyEvent.class)
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
                    trigger, result.feedVersionId(), result.routesIndexed(),
                    result.shapesIndexed(), String.format("%.2f", result.durationSeconds()));
        } catch (Exception e) {
            log.error("GTFS index reload [{}] failed: {}", trigger, e.getMessage(), e);
        }
    }
}
