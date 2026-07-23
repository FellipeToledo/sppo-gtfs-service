package com.fajtech.sppogtfs.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.LineMetadata;

import java.util.List;

/** {@code GET /lines/{lineCode}} response (§4.3). */
public record LineMetadataDto(String line, String routeLongName, List<Integer> directions,
                              int shapeCount, FeedVersionDto feedVersion) {

    public static LineMetadataDto from(LineMetadata m) {
        return new LineMetadataDto(m.line(), m.routeLongName(), m.directions(),
                m.shapeCount(), FeedVersionDto.from(m.feedVersion()));
    }
}
