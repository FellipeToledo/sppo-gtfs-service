package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.domain.RouteShape;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.EncodedShapeDto;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.GeoJsonDtos;
import com.fajtech.sppogtfs.infrastructure.config.GtfsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Standalone shape geometry (§4.3). */
@RestController
@RequestMapping("/api/v1/shapes")
public class ShapeController {

    private final GtfsQueryPort query;
    private final ResponseCache cache;
    private final OutputFormat defaultFormat;

    public ShapeController(GtfsQueryPort query, ResponseCache cache, GtfsProperties props) {
        this.query = query;
        this.cache = cache;
        this.defaultFormat = OutputFormat.parse(props.getApi().getDefaultFormat(), OutputFormat.ENCODED);
    }

    @GetMapping("/{shapeId}")
    public ResponseEntity<?> shape(
            @PathVariable String shapeId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "0") double simplify,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        if (simplify < 0) {
            throw new IllegalArgumentException("simplify must be >= 0");
        }
        OutputFormat fmt = OutputFormat.parse(format, defaultFormat);

        String etag = ETags.forShape(query.feedVersion().id(), shapeId, fmt, simplify);
        if (ETags.matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }

        Object body = cache.get(etag, () -> {
            RouteShape shape = query.findShape(shapeId)
                    .orElseThrow(() -> new ShapeNotFoundException(shapeId))
                    .simplified(simplify);
            return fmt == OutputFormat.GEOJSON
                    ? GeoJsonDtos.ShapeFeature.from(shape)
                    : EncodedShapeDto.from(shape);
        });
        return ResponseEntity.ok().eTag(etag).body(body);
    }
}
