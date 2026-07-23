package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.domain.Coordinates;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.LineItinerary;
import com.fajtech.sppogtfs.domain.RouteShape;
import com.fajtech.sppogtfs.infrastructure.config.GtfsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {LineShapesController.class, GlobalExceptionHandler.class})
@Import(LineShapesControllerTest.TestBeans.class)
class LineShapesControllerTest {

    private static final FeedVersion FEED =
            new FeedVersion("2026-07", Instant.parse("2026-07-01T00:00:00Z"), "SMTR/data.rio");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GtfsQueryPort query;

    @TestConfiguration
    static class TestBeans {
        @Bean
        GtfsProperties gtfsProperties() {
            return new GtfsProperties();
        }

        @Bean
        ResponseCache responseCache() {
            return new ResponseCache(5000, 120);
        }
    }

    private LineItinerary itinerary100() {
        RouteShape shape = RouteShape.of("S100_0", 0, "I", null,
                List.of(new Coordinates(-22.90, -43.20), new Coordinates(-22.91, -43.20)));
        return new LineItinerary(LineCode.of("100"), "Rodoviária <-> Praça XV",
                List.of(shape), FEED, LineItinerary.Resolution.EXACT);
    }

    @Test
    void returns200WithEncodedBodyAndEtag() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findLineShapes(eq("100"), anyDouble())).thenReturn(Optional.of(itinerary100()));

        mvc.perform(get("/api/v1/lines/100/shapes"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.line").value("100"))
                .andExpect(jsonPath("$.resolution").value("exact"))
                .andExpect(jsonPath("$.shapes[0].encodedPolyline").isNotEmpty())
                .andExpect(jsonPath("$.shapes[0].bbox.minLat").value(-22.91));
    }

    @Test
    void returns304WhenIfNoneMatchMatches() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findLineShapes(eq("100"), anyDouble())).thenReturn(Optional.of(itinerary100()));

        String etag = mvc.perform(get("/api/v1/lines/100/shapes"))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get("/api/v1/lines/100/shapes").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void returns404ProblemJsonForUnknownLine() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findLineShapes(eq("999"), anyDouble())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/lines/999/shapes"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Line not found"));
    }

    @Test
    void noShapesResolutionReturns200WithEmptyShapes() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        LineItinerary empty = new LineItinerary(LineCode.of("200"), "No shapes",
                List.of(), FEED, LineItinerary.Resolution.NO_SHAPES);
        when(query.findLineShapes(eq("200"), anyDouble())).thenReturn(Optional.of(empty));

        mvc.perform(get("/api/v1/lines/200/shapes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("no_shapes"))
                .andExpect(jsonPath("$.shapes").isEmpty());
    }

    @Test
    void geojsonFormatReturnsFeatureCollection() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findLineShapes(eq("100"), anyDouble())).thenReturn(Optional.of(itinerary100()));

        mvc.perform(get("/api/v1/lines/100/shapes").param("format", "geojson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("LineString"));
    }

    @Test
    void invalidFormatReturns400() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);

        mvc.perform(get("/api/v1/lines/100/shapes").param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }
}
