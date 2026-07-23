package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.BatchResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public final class BatchDtos {

    private BatchDtos() {
    }

    /** Warmup request body. */
    public record BatchRequest(@NotEmpty List<String> lines, String format, Double simplify) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchResponse(Map<String, LineItineraryDto> items,
                                List<String> unresolved,
                                FeedVersionDto feedVersion) {

        public static BatchResponse from(BatchResult result) {
            Map<String, LineItineraryDto> items = new java.util.LinkedHashMap<>();
            result.items().forEach((k, v) -> items.put(k, LineItineraryDto.from(v)));
            return new BatchResponse(items, result.unresolved(),
                    FeedVersionDto.from(result.feedVersion()));
        }
    }
}
