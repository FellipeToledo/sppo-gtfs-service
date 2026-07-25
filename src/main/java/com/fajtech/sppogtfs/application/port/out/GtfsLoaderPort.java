package com.fajtech.sppogtfs.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port: loads the raw SMTR planning data needed to build the in-memory index.
 *
 * <p>Backed in production by BigQuery ({@code rj-smtr.planejamento.*}); a demo adapter
 * provides an in-memory sample. The application layer stays free of any data technology.
 *
 * <p>The join model (real SMTR schema):
 * <ul>
 *   <li>{@code viagem_planejada_dia} → which {@code shape_id}s a {@code servico} runs
 *       (regular + {@code trajetos_alternativos}), with {@code sentido}, {@code evento},
 *       {@code modo}; carries the current {@code feed_version}.</li>
 *   <li>{@code shapes_geom} → geometry per {@code shape_id} as a WKT {@code LINESTRING}.</li>
 * </ul>
 */
public interface GtfsLoaderPort {

    /** The feed version currently in effect (from the vigente planned-trips partition). */
    FeedInfo currentFeed();

    /**
     * Distinct shape references for the current feed: one row per
     * (servico, shape_id, sentido, evento, modo), with {@code trajetos_alternativos}
     * already flattened in. Unfiltered by modo — the index applies the SPPO filter.
     */
    List<PlannedShapeRef> loadPlannedShapeRefs();

    /**
     * Geometry for the current feed's shapes: {@code shape_id} → WKT {@code LINESTRING}
     * ({@code wkt_shape}), most-recent geometry per shape_id.
     */
    List<ShapeGeometry> loadShapeGeometries();

    /**
     * Segments of the current feed's shapes, from {@code segmento_shape} — SMTR's own
     * <i>"shapes segmentados usados na validação de viagens"</i>. Carries the exclusion
     * flags verbatim; deciding what to do with them is the consumer's business rule.
     *
     * <p>May come back empty (older feed, table unavailable): the index then keeps the
     * whole-shape geometry with no exclusions, which is the previous behaviour.
     */
    List<ShapeSegment> loadShapeSegments();

    /** servico → shape_id link with direction/event/mode, from viagem_planejada_dia. */
    record PlannedShapeRef(String servico, String routeId, String shapeId,
                           String sentido, String evento, String modo, boolean alternative) {
    }

    /** shape_id → WKT LINESTRING geometry, from shapes_geom.wkt_shape. */
    record ShapeGeometry(String shapeId, String wkt) {
    }

    /**
     * One segment of a shape, from {@code segmento_shape}. Flags are the SMTR columns,
     * <b>as published</b>:
     * <ul>
     *   <li>{@code disregarded} — {@code indicador_segmento_desconsiderado}: "deve ser
     *       desconsiderado na validação de viagens". <b>É o único autoritativo</b>;</li>
     *   <li>{@code tunnel}, {@code smallSegment}, {@code damagedBuffer} — informativos.
     *       <b>Não</b> os combine para deduzir exclusão: medido no feed de 2026-07-24,
     *       77 segmentos estão em túnel e <b>não</b> são desconsiderados pela SMTR.</li>
     * </ul>
     */
    record ShapeSegment(String shapeId, String segmentId, String wkt, double lengthMeters,
                        boolean disregarded, boolean tunnel, boolean smallSegment,
                        boolean damagedBuffer) {
    }

    /** feed_version string + its start date (as an instant). */
    record FeedInfo(String feedVersion, Instant publishedAt) {
    }
}
