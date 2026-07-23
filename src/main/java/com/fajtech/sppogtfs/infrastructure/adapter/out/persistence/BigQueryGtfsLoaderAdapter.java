package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import com.fajtech.sppogtfs.infrastructure.config.GtfsProperties;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Outbound adapter reading the SMTR planning data from BigQuery
 * ({@code rj-smtr.planejamento.viagem_planejada_dia} and {@code shapes_geom}).
 *
 * <p>Read-only, run once per index reload (startup + daily cron), so the queries favor
 * correctness and partition pruning over latency. Table names come from configuration and
 * are interpolated into the SQL (they are not user input); all value predicates are bound
 * as query parameters.
 *
 * <p>Feed selection: the current feed is the {@code feed_version} of the most recent
 * available {@code data} partition of {@code viagem_planejada_dia}. Planned trips are then
 * scanned across a lookback window within that feed to capture all day-type variations
 * (Dia Útil / Sábado / Domingo). Geometry is taken as the most recent {@code wkt_shape}
 * per {@code shape_id}, decoupling from any feed-version skew between the two tables.
 */
@Component
@Profile("!demo")
public class BigQueryGtfsLoaderAdapter implements GtfsLoaderPort {

    private static final Logger log = LoggerFactory.getLogger(BigQueryGtfsLoaderAdapter.class);

    /** Days back from CURRENT_DATE to look for the most recent planned partition. */
    private static final int MAX_PARTITION_LAG_DAYS = 30;
    /** Days back from CURRENT_DATE to bound the shapes_geom scan. */
    private static final int SHAPES_SCAN_WINDOW_DAYS = 180;

    private final BigQuery bigQuery;
    private final GtfsProperties.Bigquery cfg;

    public BigQueryGtfsLoaderAdapter(BigQuery bigQuery, GtfsProperties props) {
        this.bigQuery = bigQuery;
        this.cfg = props.getBigquery();
    }

    @Override
    public FeedInfo currentFeed() {
        String sql = """
                SELECT feed_version, feed_start_date
                FROM `%s`
                WHERE data BETWEEN DATE_SUB(CURRENT_DATE(), INTERVAL @lag DAY) AND CURRENT_DATE()
                QUALIFY ROW_NUMBER() OVER (ORDER BY data DESC, feed_start_date DESC) = 1
                """.formatted(cfg.getViagemPlanejadaTable());

        for (FieldValueList row : run(QueryJobConfiguration.newBuilder(sql)
                .addNamedParameter("lag", QueryParameterValue.int64(MAX_PARTITION_LAG_DAYS))
                .build())) {
            String feedVersion = str(row, "feed_version");
            Instant publishedAt = date(row, "feed_start_date");
            return new FeedInfo(feedVersion, publishedAt);
        }
        log.warn("No planned partition found in the last {} days", MAX_PARTITION_LAG_DAYS);
        return new FeedInfo(null, null);
    }

    @Override
    public List<PlannedShapeRef> loadPlannedShapeRefs() {
        String table = cfg.getViagemPlanejadaTable();
        String sql = """
                WITH latest AS (
                  SELECT feed_version, MAX(data) OVER () AS max_data
                  FROM `%1$s`
                  WHERE data BETWEEN DATE_SUB(CURRENT_DATE(), INTERVAL @lag DAY) AND CURRENT_DATE()
                  QUALIFY ROW_NUMBER() OVER (ORDER BY data DESC, feed_start_date DESC) = 1
                ),
                scoped AS (
                  SELECT v.servico, v.route_id, v.shape_id, v.sentido, v.evento, v.modo,
                         v.trajetos_alternativos
                  FROM `%1$s` v, latest l
                  WHERE v.feed_version = l.feed_version
                    AND v.data BETWEEN DATE_SUB(l.max_data, INTERVAL @lookback DAY) AND l.max_data
                ),
                regular AS (
                  SELECT servico, route_id, shape_id, sentido, evento, modo, FALSE AS alternative
                  FROM scoped
                  WHERE shape_id IS NOT NULL
                ),
                alternates AS (
                  SELECT servico, route_id, alt.shape_id, sentido, alt.evento, modo, TRUE AS alternative
                  FROM scoped, UNNEST(trajetos_alternativos) AS alt
                  WHERE alt.shape_id IS NOT NULL
                )
                SELECT DISTINCT servico, route_id, shape_id, sentido, evento, modo, alternative
                FROM (SELECT * FROM regular UNION ALL SELECT * FROM alternates)
                """.formatted(table);

        List<PlannedShapeRef> out = new ArrayList<>();
        for (FieldValueList row : run(QueryJobConfiguration.newBuilder(sql)
                .addNamedParameter("lag", QueryParameterValue.int64(MAX_PARTITION_LAG_DAYS))
                .addNamedParameter("lookback", QueryParameterValue.int64(cfg.getPlannedLookbackDays()))
                .build())) {
            out.add(new PlannedShapeRef(
                    str(row, "servico"),
                    str(row, "route_id"),
                    str(row, "shape_id"),
                    str(row, "sentido"),
                    str(row, "evento"),
                    str(row, "modo"),
                    bool(row, "alternative")));
        }
        log.info("Loaded {} planned shape refs from BigQuery", out.size());
        return out;
    }

    @Override
    public List<ShapeGeometry> loadShapeGeometries() {
        String viagem = cfg.getViagemPlanejadaTable();
        String shapes = cfg.getShapesGeomTable();
        String sql = """
                WITH latest AS (
                  SELECT feed_version, MAX(data) OVER () AS max_data
                  FROM `%1$s`
                  WHERE data BETWEEN DATE_SUB(CURRENT_DATE(), INTERVAL @lag DAY) AND CURRENT_DATE()
                  QUALIFY ROW_NUMBER() OVER (ORDER BY data DESC, feed_start_date DESC) = 1
                ),
                needed AS (
                  SELECT DISTINCT shape_id FROM (
                    SELECT v.shape_id
                    FROM `%1$s` v, latest l
                    WHERE v.feed_version = l.feed_version
                      AND v.data BETWEEN DATE_SUB(l.max_data, INTERVAL @lookback DAY) AND l.max_data
                      AND v.shape_id IS NOT NULL
                    UNION ALL
                    SELECT alt.shape_id
                    FROM `%1$s` v, latest l, UNNEST(v.trajetos_alternativos) AS alt
                    WHERE v.feed_version = l.feed_version
                      AND v.data BETWEEN DATE_SUB(l.max_data, INTERVAL @lookback DAY) AND l.max_data
                      AND alt.shape_id IS NOT NULL
                  )
                )
                SELECT s.shape_id, s.wkt_shape
                FROM `%2$s` s
                JOIN needed n ON n.shape_id = s.shape_id
                WHERE s.feed_start_date >= DATE_SUB(CURRENT_DATE(), INTERVAL @window DAY)
                  AND s.wkt_shape IS NOT NULL
                QUALIFY ROW_NUMBER() OVER (PARTITION BY s.shape_id ORDER BY s.feed_start_date DESC) = 1
                """.formatted(viagem, shapes);

        List<ShapeGeometry> out = new ArrayList<>();
        for (FieldValueList row : run(QueryJobConfiguration.newBuilder(sql)
                .addNamedParameter("lag", QueryParameterValue.int64(MAX_PARTITION_LAG_DAYS))
                .addNamedParameter("lookback", QueryParameterValue.int64(cfg.getPlannedLookbackDays()))
                .addNamedParameter("window", QueryParameterValue.int64(SHAPES_SCAN_WINDOW_DAYS))
                .build())) {
            out.add(new ShapeGeometry(str(row, "shape_id"), str(row, "wkt_shape")));
        }
        log.info("Loaded {} shape geometries from BigQuery", out.size());
        return out;
    }

    private Iterable<FieldValueList> run(QueryJobConfiguration config) {
        try {
            JobId jobId = JobId.newBuilder()
                    .setLocation(cfg.getLocation())
                    .setJob(UUID.randomUUID().toString())
                    .build();
            TableResult result = bigQuery.query(config, jobId);
            return result.iterateAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BigQuery query interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("BigQuery query failed: " + e.getMessage(), e);
        }
    }

    private static String str(FieldValueList row, String name) {
        FieldValue v = row.get(name);
        return v == null || v.isNull() ? null : v.getStringValue();
    }

    private static boolean bool(FieldValueList row, String name) {
        FieldValue v = row.get(name);
        return v != null && !v.isNull() && v.getBooleanValue();
    }

    private static Instant date(FieldValueList row, String name) {
        String s = str(row, name);
        return s == null ? null : LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
