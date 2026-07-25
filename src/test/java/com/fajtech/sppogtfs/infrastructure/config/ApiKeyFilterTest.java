package com.fajtech.sppogtfs.infrastructure.config;

import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort;
import com.fajtech.sppogtfs.application.port.in.GtfsQueryPort.FeedSnapshot;
import com.fajtech.sppogtfs.domain.FeedVersion;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.FeedVersionController;
import com.fajtech.sppogtfs.infrastructure.adapter.in.rest.GlobalExceptionHandler;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-key gate on {@code /api/**} (§7), exercised through a real request. The filter is
 * registered by {@link WebConfig}, so it participates in the MVC slice.
 */
// A chave vem por property: GtfsProperties é @ConfigurationProperties, então o binder
// sobrescreveria qualquer valor setado à mão no @Bean com o default do application.yml.
@WebMvcTest(controllers = {FeedVersionController.class, GlobalExceptionHandler.class},
        properties = "sppo.gtfs.api.api-key=" + ApiKeyFilterTest.API_KEY)
@Import(ApiKeyFilterTest.TestBeans.class)
class ApiKeyFilterTest {

    static final String API_KEY = "public-secret";

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
    }

    private void stubFeed() {
        FeedVersion feed = new FeedVersion("2026-07", Instant.parse("2026-07-01T00:00:00Z"), "SMTR");
        when(query.feedSnapshot()).thenReturn(new FeedSnapshot(feed, 1L, 1L));
    }

    @Test
    void allowsRequestWithCorrectKey() throws Exception {
        stubFeed();

        mvc.perform(get("/api/v1/feed/version").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void deniesRequestWithoutKeyAsProblemJson() throws Exception {
        mvc.perform(get("/api/v1/feed/version"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/v1/feed/version"));
    }

    @Test
    void deniesRequestWithWrongKey() throws Exception {
        mvc.perform(get("/api/v1/feed/version").header("X-Api-Key", "nope"))
                .andExpect(status().isUnauthorized());
    }
}
