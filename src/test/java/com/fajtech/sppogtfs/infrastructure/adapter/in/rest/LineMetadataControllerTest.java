package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.LineMetadata;
import com.fajtech.sppogtfs.domain.FeedVersion;
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

/** Line metadata endpoint: {@code GET /api/v1/lines/{lineCode}} (§4.3). */
@WebMvcTest(controllers = {LineMetadataController.class, GlobalExceptionHandler.class})
@Import(LineMetadataControllerTest.TestBeans.class)
class LineMetadataControllerTest {

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

    @Test
    void returnsDirectionsAndShapeCount() throws Exception {
        when(query.findLineMetadata("100")).thenReturn(Optional.of(
                new LineMetadata("100", "Rodoviária <-> Praça XV", List.of(0, 1), 3, FEED)));

        mvc.perform(get("/api/v1/lines/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line").value("100"))
                .andExpect(jsonPath("$.routeLongName").value("Rodoviária <-> Praça XV"))
                .andExpect(jsonPath("$.directions[0]").value(0))
                .andExpect(jsonPath("$.directions[1]").value(1))
                .andExpect(jsonPath("$.shapeCount").value(3))
                .andExpect(jsonPath("$.feedVersion.id").value("2026-07"));
    }

    @Test
    void returns404ProblemJsonForUnknownLine() throws Exception {
        when(query.findLineMetadata("999")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/lines/999"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Line not found"));
    }

    @Test
    void existingLineWithoutShapesStillReturns200() throws Exception {
        when(query.findLineMetadata("200")).thenReturn(Optional.of(
                new LineMetadata("200", "No shapes", List.of(), 0, FEED)));

        mvc.perform(get("/api/v1/lines/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shapeCount").value(0))
                .andExpect(jsonPath("$.directions").isEmpty());
    }
}
