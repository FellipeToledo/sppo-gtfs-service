package com.fajtech.sppogtfs.domain;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeoAndShapeTest {

    @Test
    void haversineMatchesKnownDistance() {
        // ~1.11 km per 0.01 deg latitude near the equator/Rio.
        double d = GeoMath.haversineMeters(
                new Coordinates(-22.9000, -43.2000),
                new Coordinates(-22.9100, -43.2000));
        assertThat(d).isCloseTo(1111.0, Offset.offset(5.0));
    }

    @Test
    void routeShapeDerivesLengthBboxAndCounts() {
        List<Coordinates> pts = List.of(
                new Coordinates(-22.90, -43.20),
                new Coordinates(-22.91, -43.20),
                new Coordinates(-22.92, -43.19));
        RouteShape s = RouteShape.of("100_0_1", 0, "I", null, pts);

        assertThat(s.pointCount()).isEqualTo(3);
        assertThat(s.sentido()).isEqualTo("I");
        assertThat(s.lengthMeters()).isGreaterThan(2000.0);
        assertThat(s.bbox().minLat()).isEqualTo(-22.92);
        assertThat(s.bbox().maxLat()).isEqualTo(-22.90);
        assertThat(s.bbox().minLon()).isEqualTo(-43.20);
        assertThat(s.bbox().maxLon()).isEqualTo(-43.19);
        assertThat(s.encodedPolyline()).isNotBlank();
    }

    @Test
    void douglasPeuckerDropsCollinearPoints() {
        List<Coordinates> straight = List.of(
                new Coordinates(-22.90, -43.20),
                new Coordinates(-22.905, -43.20),  // collinear midpoint
                new Coordinates(-22.91, -43.20));
        List<Coordinates> reduced = DouglasPeucker.simplify(straight, 2.0);
        assertThat(reduced).hasSize(2);
    }
}
