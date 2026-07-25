package com.fajtech.sppogtfs.domain;

import java.util.List;

/**
 * One segment of an itinerary, from SMTR's {@code segmento_shape} — the table it describes
 * as <i>"shapes segmentados usados na validação de viagens"</i>. This is where the corridor
 * is <b>defined</b>, so the flags are carried <b>exactly as published</b> and no rule is
 * derived here.
 *
 * @param disregarded    {@code indicador_segmento_desconsiderado}: "deve ser desconsiderado
 *                       na validação de viagens". <b>The only authoritative exclusion.</b>
 * @param tunnel         {@code indicador_tunel} — informative
 * @param smallSegment   {@code indicador_segmento_pequeno} (&lt; 990 m) — informative
 * @param damagedBuffer  {@code indicador_area_prejudicada} (buffer lost &gt;50% of its area
 *                       in the treatment) — informative
 *
 * <p>⚠️ <b>Do not compose exclusion from the informative flags.</b> Measured on the
 * 2026-07-24 feed: 77 segments are in a tunnel and are <b>not</b> disregarded by SMTR, so
 * {@code tunnel OR smallSegment OR damagedBuffer} would exclude route that SMTR validates.
 * They are exposed because the per-segment trip validation will need them (backend's
 * {@code docs/regras-de-negocio.md} §11), not to be recombined.
 */
public record RouteSegment(
        String segmentId,
        List<Coordinates> points,
        double lengthMeters,
        boolean disregarded,
        boolean tunnel,
        boolean smallSegment,
        boolean damagedBuffer) {

    public RouteSegment {
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("segmentId must not be blank");
        }
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException(
                    "segment needs at least 2 points: " + segmentId);
        }
        points = List.copyOf(points);
    }

    /** True when this segment takes part in the corridor test (SMTR's own criterion). */
    public boolean participatesInValidation() {
        return !disregarded;
    }
}
