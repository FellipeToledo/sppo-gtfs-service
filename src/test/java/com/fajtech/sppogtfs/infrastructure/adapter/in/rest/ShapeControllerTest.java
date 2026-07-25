package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.domain.Coordinates;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.RouteShape;
import com.fajtech.sppogtfs.infrastructure.config.GtfsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone shape endpoint: {@code GET /api/v1/shapes/{shapeId}} (§4.3). */
@WebMvcTest(controllers = {ShapeController.class, GlobalExceptionHandler.class})
@Import(ShapeControllerTest.TestBeans.class)
class ShapeControllerTest {

    private static final FeedVersion FEED =
            new FeedVersion("2026-07", Instant.parse("2026-07-01T00:00:00Z"), "SMTR/BigQuery");

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

    private RouteShape shape() {
        return RouteShape.of("S100_0", 0, "I", null,
                List.of(new Coordinates(-22.90, -43.20), new Coordinates(-22.91, -43.20)));
    }

    @Test
    void returns200WithEncodedShapeAndEtag() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findShape("S100_0")).thenReturn(Optional.of(shape()));

        mvc.perform(get("/api/v1/shapes/S100_0"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.shapeId").value("S100_0"))
                .andExpect(jsonPath("$.directionId").value(0))
                .andExpect(jsonPath("$.encodedPolyline").isNotEmpty())
                .andExpect(jsonPath("$.bbox.minLat").value(-22.91))
                .andExpect(jsonPath("$.pointCount").value(2));
    }

    @Test
    void returns304WhenIfNoneMatchMatches() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findShape("S100_0")).thenReturn(Optional.of(shape()));

        String etag = mvc.perform(get("/api/v1/shapes/S100_0"))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get("/api/v1/shapes/S100_0").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }

    @Test
    void returns404ProblemJsonForUnknownShape() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findShape("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/shapes/nope"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Shape not found"));
    }

    @Test
    void geojsonFormatReturnsFeatureWithLonLatOrder() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findShape("S100_0")).thenReturn(Optional.of(shape()));

        mvc.perform(get("/api/v1/shapes/S100_0").param("format", "geojson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Feature"))
                .andExpect(jsonPath("$.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.geometry.coordinates[0][0]").value(-43.20))
                .andExpect(jsonPath("$.geometry.coordinates[0][1]").value(-22.90))
                .andExpect(jsonPath("$.properties.shapeId").value("S100_0"));
    }

    @Test
    void etagVariesWithFormat() throws Exception {
        when(query.feedVersion()).thenReturn(FEED);
        when(query.findShape("S100_0")).thenReturn(Optional.of(shape()));

        String encoded = mvc.perform(get("/api/v1/shapes/S100_0"))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get("/api/v1/shapes/S100_0").param("format", "geojson")
                        .header("If-None-Match", encoded))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsNegativeSimplify() throws Exception {
        mvc.perform(get("/api/v1/shapes/S100_0").param("simplify", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }
}
