package com.fajtech.sppogtfs.infrastructure.config;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import com.fajtech.sppogtfs.application.port.out.MetricsPort;
import com.fajtech.sppogtfs.application.service.GtfsIndexBuilder;
import com.fajtech.sppogtfs.application.service.GtfsQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free application service to its ports. Keeps Spring annotations out
 * of the application layer (dependencies point inward).
 */
@Configuration
public class GtfsServiceConfig {

    @Bean
    public GtfsIndexBuilder gtfsIndexBuilder() {
        return new GtfsIndexBuilder();
    }

    /**
     * Single service bean implementing both {@link GtfsQueryPort} and {@link ReloadIndexPort};
     * consumers inject by either interface.
     */
    @Bean
    public GtfsQueryService gtfsQueryService(GtfsLoaderPort loader, GtfsIndexBuilder builder,
                                             MetricsPort metrics, GtfsProperties props) {
        var filter = new GtfsIndexBuilder.FilterConfig(props.getFilter().getModos());
        var settings = new GtfsQueryService.Settings(filter, props.getFeed().getSource());
        return new GtfsQueryService(loader, builder, metrics, settings);
    }
}
