package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.LineMetadataDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Light line metadata (§4.3): names, available directions, shape count. */
@RestController
@RequestMapping("/api/v1/lines")
public class LineMetadataController {

    private final GtfsQueryPort query;

    public LineMetadataController(GtfsQueryPort query) {
        this.query = query;
    }

    @GetMapping("/{lineCode}")
    public ResponseEntity<LineMetadataDto> metadata(@PathVariable String lineCode) {
        return query.findLineMetadata(lineCode)
                .map(LineMetadataDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new LineNotFoundException(lineCode));
    }
}
