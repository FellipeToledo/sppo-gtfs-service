package com.fajtech.sppogtfs.infrastructure.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the API-key filter and restricts CORS to the configured origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GtfsProperties props;

    public WebConfig(GtfsProperties props) {
        this.props = props;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ObjectMapper mapper) {
        var filter = new ApiKeyFilter(
                props.getApi().getApiKey(), props.getApi().getAdminKey(), mapper);
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*", "/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = props.getApi().getCorsAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            registry.addMapping("/api/**")
                    .allowedOrigins(origins.toArray(String[]::new))
                    .allowedMethods("GET", "POST")
                    .allowedHeaders("Content-Type", "X-Api-Key", "If-None-Match")
                    .exposedHeaders("ETag");
        }
    }
}
