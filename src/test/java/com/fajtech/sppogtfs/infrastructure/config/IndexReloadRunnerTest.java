package com.fajtech.sppogtfs.infrastructure.config;

import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexReloadRunnerTest {

    private final ReloadIndexPort reload = mock(ReloadIndexPort.class);

    private static GtfsProperties propsWithStartupLoad(boolean onStartup) {
        GtfsProperties props = new GtfsProperties();
        props.getReload().setOnStartup(onStartup);
        return props;
    }

    /**
     * Guards the startup-readiness contract. Spring Boot flips readiness to
     * ACCEPTING_TRAFFIC on ApplicationReadyEvent, which fires *after*
     * ApplicationStartedEvent — so loading on "started" is what makes
     * /actuator/health/readiness (and the container healthcheck) mean "index is usable".
     * Moving this listener to ApplicationReadyEvent reopens the race that had a consumer
     * resolving 285 lines against an empty index in the first minute.
     */
    @Test
    void startupLoadMustRunBeforeReadinessFlips() throws NoSuchMethodException {
        Method onStartup = IndexReloadRunner.class.getMethod("onStartup");

        EventListener annotation = onStartup.getAnnotation(EventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(ApplicationStartedEvent.class);
    }

    @Test
    void shouldLoadTheIndexOnStartup() {
        when(reload.reload()).thenReturn(new ReloadIndexPort.ReloadResult("2026-07", 457, 1157, 21.5));

        new IndexReloadRunner(reload, propsWithStartupLoad(true)).onStartup();

        verify(reload).reload();
    }

    @Test
    void shouldSkipTheStartupLoadWhenDisabled() {
        new IndexReloadRunner(reload, propsWithStartupLoad(false)).onStartup();

        verify(reload, never()).reload();
    }

    @Test
    void shouldNotPropagateAFailedReload() {
        // A BigQuery hiccup must not abort startup: the service comes up with an empty
        // index (consumers treat empty as "no itinerary") and the daily cron retries.
        when(reload.reload()).thenThrow(new IllegalStateException("BigQuery unavailable"));

        new IndexReloadRunner(reload, propsWithStartupLoad(true)).onStartup();

        verify(reload).reload();
    }
}
