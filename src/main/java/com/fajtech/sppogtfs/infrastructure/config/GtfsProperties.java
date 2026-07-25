package com.fajtech.sppogtfs.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Externalized configuration, prefix {@code sppo.gtfs} (§8).
 */
@ConfigurationProperties(prefix = "sppo.gtfs")
public class GtfsProperties {

    private Bigquery bigquery = new Bigquery();
    private Filter filter = new Filter();
    private Reload reload = new Reload();
    private Api api = new Api();
    private Feed feed = new Feed();

    public Bigquery getBigquery() {
        return bigquery;
    }

    public void setBigquery(Bigquery bigquery) {
        this.bigquery = bigquery;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public Reload getReload() {
        return reload;
    }

    public void setReload(Reload reload) {
        this.reload = reload;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Feed getFeed() {
        return feed;
    }

    public void setFeed(Feed feed) {
        this.feed = feed;
    }

    public static class Bigquery {
        /** GCP project that hosts the datasets (billing/execution project). */
        private String projectId = "";
        /** Dataset holding the planning tables. */
        private String dataset = "planejamento";
        /** Fully-qualified table (project.dataset.table) for planned trips. */
        private String viagemPlanejadaTable = "rj-smtr.planejamento.viagem_planejada_dia";
        /** Fully-qualified table for shape geometry. */
        private String shapesGeomTable = "rj-smtr.planejamento.shapes_geom";
        /** BigQuery processing location (e.g. US, southamerica-east1). */
        private String location = "US";
        /**
         * How many days back from the latest planned partition to scan, to capture all
         * day-type variations (Dia Útil / Sábado / Domingo) within the current feed.
         */
        private int plannedLookbackDays = 14;

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getDataset() {
            return dataset;
        }

        public void setDataset(String dataset) {
            this.dataset = dataset;
        }

        public String getViagemPlanejadaTable() {
            return viagemPlanejadaTable;
        }

        public void setViagemPlanejadaTable(String viagemPlanejadaTable) {
            this.viagemPlanejadaTable = viagemPlanejadaTable;
        }

        public String getShapesGeomTable() {
            return shapesGeomTable;
        }

        public void setShapesGeomTable(String shapesGeomTable) {
            this.shapesGeomTable = shapesGeomTable;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public int getPlannedLookbackDays() {
            return plannedLookbackDays;
        }

        public void setPlannedLookbackDays(int plannedLookbackDays) {
            this.plannedLookbackDays = plannedLookbackDays;
        }
    }

    public static class Filter {
        /** SMTR 'modo' values to include (SPPO = Ônibus). Compared case/space-insensitively. */
        private Set<String> modos = Set.of("Ônibus");

        public Set<String> getModos() {
            return modos;
        }

        public void setModos(Set<String> modos) {
            this.modos = modos;
        }
    }

    public static class Reload {
        private boolean onStartup = true;
        /** Daily reload cron (UTC). */
        private String cron = "0 0 4 * * *";
        /**
         * Attempts for the <b>startup</b> load. A transient BigQuery failure would
         * otherwise leave the index empty until the daily cron. Only the startup load
         * retries: a failed scheduled reload keeps the previous index (atomic swap), so
         * there is nothing to recover from.
         */
        private int startupAttempts = 3;
        /**
         * Wait before the 2nd startup attempt; doubles on each further attempt
         * (10s → 20s → …). Keep {@code attempts × (load + backoff)} inside the
         * container healthcheck {@code start-period}, since readiness only flips after
         * this finishes.
         */
        private Duration startupBackoff = Duration.ofSeconds(10);

        public boolean isOnStartup() {
            return onStartup;
        }

        public void setOnStartup(boolean onStartup) {
            this.onStartup = onStartup;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getStartupAttempts() {
            return startupAttempts;
        }

        public void setStartupAttempts(int startupAttempts) {
            this.startupAttempts = startupAttempts;
        }

        public Duration getStartupBackoff() {
            return startupBackoff;
        }

        public void setStartupBackoff(Duration startupBackoff) {
            this.startupBackoff = startupBackoff;
        }
    }

    public static class Api {
        /** Default polyline format: encoded | geojson. */
        private String defaultFormat = "encoded";
        /** Optional API key. Empty = no requirement. */
        private String apiKey = "";
        /** Optional admin key for /internal endpoints. Empty = falls back to api-key. */
        private String adminKey = "";
        /** Allowed CORS origins. */
        private List<String> corsAllowedOrigins = List.of();

        public String getDefaultFormat() {
            return defaultFormat;
        }

        public void setDefaultFormat(String defaultFormat) {
            this.defaultFormat = defaultFormat;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAdminKey() {
            return adminKey;
        }

        public void setAdminKey(String adminKey) {
            this.adminKey = adminKey;
        }

        public List<String> getCorsAllowedOrigins() {
            return corsAllowedOrigins;
        }

        public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
            this.corsAllowedOrigins = corsAllowedOrigins;
        }
    }

    public static class Feed {
        /** Human label for the feed source, used as FeedVersion.source. */
        private String source = "SMTR/BigQuery rj-smtr.planejamento";

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
