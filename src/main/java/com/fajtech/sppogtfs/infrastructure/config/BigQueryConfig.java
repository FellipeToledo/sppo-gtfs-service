package com.fajtech.sppogtfs.infrastructure.config;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Builds the BigQuery client. Credentials come from Application Default Credentials
 * (e.g. {@code GOOGLE_APPLICATION_CREDENTIALS} pointing at a service-account key).
 * Not created under the {@code demo} profile, which uses an in-memory loader.
 */
@Configuration
@Profile("!demo")
public class BigQueryConfig {

    @Bean
    public BigQuery bigQuery(GtfsProperties props) {
        BigQueryOptions.Builder builder = BigQueryOptions.newBuilder();
        String projectId = props.getBigquery().getProjectId();
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        return builder.build().getService();
    }
}
