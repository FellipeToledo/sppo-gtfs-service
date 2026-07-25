package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.FeedSnapshot;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.infrastructure.config.GtfsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feed version endpoint: {@code GET /api/v1/feed/version} (§4.4). The backend polls this
 * to invalidate its cache, so the {@code id} and the ETag must track the loaded feed.
 */
@WebMvcTest(controllers = {FeedVersionController.class, GlobalExceptionHandler.class})
@Import(FeedVersionControllerTest.TestBeans.class)
class FeedVersionControllerTest {

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
    void returnsFeedVersionWithCountsAndEtag() throws Exception {
        FeedVersion feed = new FeedVersion("2026-07-18 13:00:00-03:00",
                Instant.parse("2026-07-19T00:00:00Z"), "SMTR/BigQuery rj-smtr.planejamento");
        when(query.feedSnapshot()).thenReturn(new FeedSnapshot(feed, 1234L, 5678L));

        mvc.perform(get("/api/v1/feed/version"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2026-07-18 13:00:00-03:00\""))
                .andExpect(jsonPath("$.id").value("2026-07-18 13:00:00-03:00"))
                .andExpect(jsonPath("$.source").value("SMTR/BigQuery rj-smtr.planejamento"))
                .andExpect(jsonPath("$.counts.lines").value(1234))
                .andExpect(jsonPath("$.counts.shapes").value(5678));
    }

    @Test
    void etagChangesWhenFeedChanges() throws Exception {
        FeedVersion first = new FeedVersion("feed-1", Instant.parse("2026-07-01T00:00:00Z"), "SMTR");
        when(query.feedSnapshot()).thenReturn(new FeedSnapshot(first, 1L, 1L));
        String etag1 = mvc.perform(get("/api/v1/feed/version"))
                .andReturn().getResponse().getHeader("ETag");

        FeedVersion second = new FeedVersion("feed-2", Instant.parse("2026-07-20T00:00:00Z"), "SMTR");
        when(query.feedSnapshot()).thenReturn(new FeedSnapshot(second, 2L, 2L));
        String etag2 = mvc.perform(get("/api/v1/feed/version"))
                .andReturn().getResponse().getHeader("ETag");

        org.assertj.core.api.Assertions.assertThat(etag1).isNotEqualTo(etag2);
    }
}
