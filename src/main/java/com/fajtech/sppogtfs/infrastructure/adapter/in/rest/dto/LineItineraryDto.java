package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.domain.LineItinerary;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Aggregated per-line response in the default (encoded) form.
 * {@code resolution} is one of {@code exact | relaxed | no_shapes}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LineItineraryDto(
        String line,
        String routeLongName,
        FeedVersionDto feedVersion,
        String resolution,
        List<EncodedShapeDto> shapes) {

    public static LineItineraryDto from(LineItinerary it) {
        return new LineItineraryDto(
                it.line().value(),
                it.routeLongName(),
                FeedVersionDto.from(it.feedVersion()),
                it.resolution().name().toLowerCase(),
                it.shapes().stream().map(EncodedShapeDto::from).toList());
    }
}
