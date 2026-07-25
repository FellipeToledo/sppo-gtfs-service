package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.BatchResult;
import com.fajtech.sppogtfs.domain.Coordinates;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.domain.LineCode;
import com.fajtech.sppogtfs.domain.LineItinerary;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Batch warmup endpoint: {@code POST /api/v1/lines/shapes:batch} (§4.2). */
@WebMvcTest(controllers = {BatchController.class, GlobalExceptionHandler.class})
@Import(BatchControllerTest.TestBeans.class)
class BatchControllerTest {

    private static final FeedVersion FEED =
            new FeedVersion("2026-07", Instant.parse("2026-07-01T00:00:00Z"), "SMTR/BigQuery");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GtfsQueryPort query;

    /** {@code WebConfig} é um WebMvcConfigurer, então o @WebMvcTest o carrega e exige as props. */
    @TestConfiguration
    static class TestBeans {
        @Bean
        GtfsProperties gtfsProperties() {
            return new GtfsProperties();
        }
    }

    private LineItinerary itinerary100() {
        RouteShape shape = RouteShape.of("S100_0", 0, "I", null,
                List.of(new Coordinates(-22.90, -43.20), new Coordinates(-22.91, -43.20)));
        return new LineItinerary(LineCode.of("100"), "Rodoviária <-> Praça XV",
                List.of(shape), FEED, LineItinerary.Resolution.EXACT);
    }

    @Test
    void resolvesKnownLinesAndListsUnresolvedOnes() throws Exception {
        when(query.findLineShapesBatch(anyList(), anyDouble()))
                .thenReturn(new BatchResult(Map.of("100", itinerary100()), List.of("999"), FEED));

        mvc.perform(post("/api/v1/lines/shapes:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[\"100\",\"999\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.100.resolution").value("exact"))
                .andExpect(jsonPath("$.items.100.shapes[0].encodedPolyline").isNotEmpty())
                .andExpect(jsonPath("$.unresolved[0]").value("999"))
                .andExpect(jsonPath("$.feedVersion.id").value("2026-07"));
    }

    @Test
    void missingSimplifyDefaultsToZero() throws Exception {
        when(query.findLineShapesBatch(anyList(), anyDouble()))
                .thenReturn(new BatchResult(Map.of(), List.of(), FEED));

        mvc.perform(post("/api/v1/lines/shapes:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[\"100\"]}"))
                .andExpect(status().isOk());

        verify(query).findLineShapesBatch(List.of("100"), 0.0);
    }

    @Test
    void rejectsNegativeSimplifyWithProblemJson() throws Exception {
        mvc.perform(post("/api/v1/lines/shapes:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[\"100\"],\"simplify\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Invalid request"));

        verify(query, never()).findLineShapesBatch(anyList(), anyDouble());
    }

    @Test
    void rejectsEmptyLineList() throws Exception {
        mvc.perform(post("/api/v1/lines/shapes:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        verify(query, never()).findLineShapesBatch(anyList(), anyDouble());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/lines/shapes:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }
}
