package com.fajtech.sppogtfs.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolylineEncoderTest {

    @Test
    void encodesGoogleReferenceExample() {
        // Canonical example from Google's Encoded Polyline Algorithm Format docs.
        List<Coordinates> points = List.of(
                new Coordinates(38.5, -120.2),
                new Coordinates(40.7, -120.95),
                new Coordinates(43.252, -126.453));
        assertThat(PolylineEncoder.encode(points)).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }

    @Test
    void roundTripsWithinPrecision() {
        List<Coordinates> points = List.of(
                new Coordinates(-22.9068, -43.1729),
                new Coordinates(-22.9028, -43.1800),
                new Coordinates(-22.8100, -43.2400));
        List<Coordinates> decoded = PolylineEncoder.decode(PolylineEncoder.encode(points));
        assertThat(decoded).hasSize(points.size());
        for (int i = 0; i < points.size(); i++) {
            assertThat(decoded.get(i).latitude()).isCloseTo(points.get(i).latitude(), org.assertj.core.data.Offset.offset(1e-5));
            assertThat(decoded.get(i).longitude()).isCloseTo(points.get(i).longitude(), org.assertj.core.data.Offset.offset(1e-5));
        }
    }
}
