package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.LineItinerary;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.GeoJsonDtos;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.LineItineraryDto;
import com.fajtech.sppogtfs.infrastructure.config.GtfsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Critical endpoint: all shapes of a line (§4.1). Encoded polyline by default; GeoJSON on
 * request; optional Douglas–Peucker simplification. Content ETag + If-None-Match → 304.
 */
@RestController
@RequestMapping("/api/v1/lines")
public class LineShapesController {

    private final GtfsQueryPort query;
    private final ResponseCache cache;
    private final OutputFormat defaultFormat;

    public LineShapesController(GtfsQueryPort query, ResponseCache cache, GtfsProperties props) {
        this.query = query;
        this.cache = cache;
        this.defaultFormat = OutputFormat.parse(props.getApi().getDefaultFormat(), OutputFormat.ENCODED);
    }

    @GetMapping("/{lineCode}/shapes")
    public ResponseEntity<?> shapes(
            @PathVariable String lineCode,
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "0") double simplify,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        if (simplify < 0) {
            throw new IllegalArgumentException("simplify must be >= 0");
        }
        OutputFormat fmt = OutputFormat.parse(format, defaultFormat);
        String normalized = LineCode.of(lineCode).value();

        String etag = ETags.forLine(query.feedVersion().id(), normalized, fmt, simplify);
        if (ETags.matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }

        Object body = cache.get(etag, () -> {
            LineItinerary itinerary = query.findLineShapes(lineCode, simplify)
                    .orElseThrow(() -> new LineNotFoundException(lineCode));
            return fmt == OutputFormat.GEOJSON
                    ? GeoJsonDtos.FeatureCollection.from(itinerary)
                    : LineItineraryDto.from(itinerary);
        });

        return ResponseEntity.ok().eTag(etag).body(body);
    }
}
