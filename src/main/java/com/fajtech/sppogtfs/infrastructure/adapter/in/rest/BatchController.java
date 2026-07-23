package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.BatchDtos.BatchRequest;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.BatchDtos.BatchResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch warmup (§4.2): resolve many lines in one call so the backend can pre-warm its
 * cache at boot. Always returns the encoded form; {@code simplify} is honored.
 */
@RestController
@RequestMapping("/api/v1/lines")
public class BatchController {

    private final GtfsQueryPort query;

    public BatchController(GtfsQueryPort query) {
        this.query = query;
    }

    @PostMapping("/shapes:batch")
    public ResponseEntity<BatchResponse> batch(@Valid @RequestBody BatchRequest request) {
        double simplify = request.simplify() == null ? 0.0 : request.simplify();
        if (simplify < 0) {
            throw new IllegalArgumentException("simplify must be >= 0");
        }
        var result = query.findLineShapesBatch(request.lines(), simplify);
        return ResponseEntity.ok(BatchResponse.from(result));
    }
}
