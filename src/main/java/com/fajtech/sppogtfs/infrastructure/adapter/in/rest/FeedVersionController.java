package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto.FeedVersionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Feed version + counts (§4.4). The backend polls this to invalidate its cache. */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedVersionController {

    private final GtfsQueryPort query;

    public FeedVersionController(GtfsQueryPort query) {
        this.query = query;
    }

    @GetMapping("/version")
    public ResponseEntity<FeedVersionResponse> version() {
        var snapshot = query.feedSnapshot();
        String etag = "\"" + snapshot.feedVersion().id() + "\"";
        return ResponseEntity.ok().eTag(etag).body(FeedVersionResponse.from(snapshot));
    }
}
