package com.fajtech.sppogtfs.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SMTR's segment ({@code segmento_shape}) as carried by the service: flags verbatim, no
 * rule derived here.
 */
class RouteSegmentTest {

    private static List<Coordinates> line() {
        return List.of(new Coordinates(-22.90, -43.20), new Coordinates(-22.90, -43.19));
    }

    private static RouteSegment segment(boolean disregarded, boolean tunnel) {
        return new RouteSegment("s1-1", line(), 1113.0, disregarded, tunnel, false, false);
    }

    @Test
    void onlyDisregardedTakesTheSegmentOutOfValidation() {
        assertThat(segment(false, false).participatesInValidation()).isTrue();
        assertThat(segment(true, false).participatesInValidation()).isFalse();
    }

    @Test
    void tunnelAloneDoesNotExcludeTheSegment() {
        // Measured on the 2026-07-24 feed: 77 tunnel segments are NOT disregarded by SMTR.
        // Composing exclusion out of the informative flags would drop valid route.
        assertThat(segment(false, true).participatesInValidation()).isTrue();
        assertThat(segment(false, true).tunnel()).isTrue();
    }

    @Test
    void shouldRejectASegmentWithoutALine() {
        assertThatThrownBy(() -> new RouteSegment("s1-1",
                List.of(new Coordinates(-22.90, -43.20)), 10.0, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 points");
    }

    @Test
    void shouldRejectABlankSegmentId() {
        assertThatThrownBy(() -> new RouteSegment(" ", line(), 10.0, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shapeKeepsSegmentsThroughSimplification() {
        // Simplification is for drawing; segments define the rule and must not be reduced.
        RouteShape shape = RouteShape.of("s1", 0, "I", null, List.of(
                        new Coordinates(-22.9000, -43.2000),
                        new Coordinates(-22.9000, -43.1999),
                        new Coordinates(-22.9000, -43.1900)))
                .withSegments(List.of(segment(true, false)));

        RouteShape simplified = shape.simplified(50);

        assertThat(simplified.segments()).hasSize(1);
        assertThat(simplified.disregardedSegmentCount()).isEqualTo(1);
    }

    @Test
    void shapeWithoutSegmentsIsLegitimate() {
        RouteShape shape = RouteShape.of("s1", 0, "I", null, line());

        assertThat(shape.segments()).isEmpty();
        assertThat(shape.disregardedSegmentCount()).isZero();
    }
}
