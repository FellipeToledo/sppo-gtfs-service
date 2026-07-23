package com.fajtech.sppogtfs.domain;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WktLineStringTest {

    @Test
    void parsesLonLatOrderIntoLatLon() {
        List<Coordinates> pts = WktLineString.parse("LINESTRING(-43.20 -22.90, -43.19 -22.91)");
        assertThat(pts).hasSize(2);
        assertThat(pts.get(0).latitude()).isCloseTo(-22.90, Offset.offset(1e-9));
        assertThat(pts.get(0).longitude()).isCloseTo(-43.20, Offset.offset(1e-9));
        assertThat(pts.get(1).latitude()).isCloseTo(-22.91, Offset.offset(1e-9));
    }

    @Test
    void toleratesExtraWhitespaceAndCaseAndSridPrefix() {
        List<Coordinates> pts = WktLineString.parse(
                "SRID=4326;linestring ( -43.2  -22.9 ,  -43.1 -22.8 )");
        assertThat(pts).hasSize(2);
        assertThat(pts.get(1).longitude()).isCloseTo(-43.1, Offset.offset(1e-9));
    }

    @Test
    void ignoresZOrdinate() {
        List<Coordinates> pts = WktLineString.parse("LINESTRING(-43.2 -22.9 0, -43.1 -22.8 5)");
        assertThat(pts).hasSize(2);
        assertThat(pts.get(0).latitude()).isCloseTo(-22.9, Offset.offset(1e-9));
    }

    @Test
    void rejectsNonLineString() {
        assertThatThrownBy(() -> WktLineString.parse("POINT(-43.2 -22.9)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> WktLineString.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
