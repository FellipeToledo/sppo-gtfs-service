package com.fajtech.sppogtfs.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * Externalized configuration, prefix {@code sppo.gtfs} (§8).
 */
@ConfigurationProperties(prefix = "sppo.gtfs")
public class GtfsProperties {

    private Filter filter = new Filter();
    private Reload reload = new Reload();
    private Api api = new Api();
    private Feed feed = new Feed();

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

    public static class Filter {
        /** GTFS route_type values to include. SPPO = buses (3). */
        private Set<Integer> routeTypes = Set.of(3);
        private boolean excludeBrt = true;
        /** Agencies considered BRT (excluded when exclude-brt=true). */
        private Set<String> brtAgencyIds = Set.of();
        /** Line short-name prefixes considered BRT. */
        private List<String> brtLinePrefixes = List.of();

        public Set<Integer> getRouteTypes() {
            return routeTypes;
        }

        public void setRouteTypes(Set<Integer> routeTypes) {
            this.routeTypes = routeTypes;
        }

        public boolean isExcludeBrt() {
            return excludeBrt;
        }

        public void setExcludeBrt(boolean excludeBrt) {
            this.excludeBrt = excludeBrt;
        }

        public Set<String> getBrtAgencyIds() {
            return brtAgencyIds;
        }

        public void setBrtAgencyIds(Set<String> brtAgencyIds) {
            this.brtAgencyIds = brtAgencyIds;
        }

        public List<String> getBrtLinePrefixes() {
            return brtLinePrefixes;
        }

        public void setBrtLinePrefixes(List<String> brtLinePrefixes) {
            this.brtLinePrefixes = brtLinePrefixes;
        }
    }

    public static class Reload {
        private boolean onStartup = true;
        /** Daily reload cron (UTC). */
        private String cron = "0 0 4 * * *";

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
        /** Human label for the feed source. */
        private String source = "SMTR/data.rio";
        /** Fallback feed id when the DB has no feed_info row. Empty = current yyyy-MM. */
        private String fallbackId = "";

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getFallbackId() {
            return fallbackId;
        }

        public void setFallbackId(String fallbackId) {
            this.fallbackId = fallbackId;
        }
    }
}
