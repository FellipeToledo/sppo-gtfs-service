package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort;
import com.fajtech.sppogtfs.application.port.in.ReloadIndexPort.ReloadResult;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin reload endpoint: {@code POST /internal/reload} (§6), incluindo o gate do
 * {@code ApiKeyFilter} — o filtro entra no teste porque o {@code WebConfig} o registra.
 */
// A chave vem por property: GtfsProperties é @ConfigurationProperties, então o binder
// sobrescreveria qualquer valor setado à mão no @Bean com o default do application.yml.
@WebMvcTest(controllers = {InternalReloadController.class, GlobalExceptionHandler.class},
        properties = "sppo.gtfs.api.admin-key=" + InternalReloadControllerTest.ADMIN_KEY)
@Import(InternalReloadControllerTest.TestBeans.class)
class InternalReloadControllerTest {

    static final String ADMIN_KEY = "admin-secret";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReloadIndexPort reload;

    @TestConfiguration
    static class TestBeans {
        @Bean
        GtfsProperties gtfsProperties() {
            return new GtfsProperties();
        }
    }

    @Test
    void returnsIndexSummaryAfterReload() throws Exception {
        when(reload.reload()).thenReturn(new ReloadResult("2026-07", 1234L, 5678L, 4.2));

        mvc.perform(post("/internal/reload").header("X-Api-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedVersionId").value("2026-07"))
                .andExpect(jsonPath("$.linesIndexed").value(1234))
                .andExpect(jsonPath("$.shapesIndexed").value(5678))
                .andExpect(jsonPath("$.durationSeconds").value(4.2));

        verify(reload).reload();
    }

    @Test
    void deniesWithoutAdminKey() throws Exception {
        mvc.perform(post("/internal/reload"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        verify(reload, never()).reload();
    }

    @Test
    void deniesWithWrongAdminKey() throws Exception {
        mvc.perform(post("/internal/reload").header("X-Api-Key", "nope"))
                .andExpect(status().isUnauthorized());

        verify(reload, never()).reload();
    }

    @Test
    void failureIsReportedAsProblemJsonWithoutStackTrace() throws Exception {
        when(reload.reload()).thenThrow(new IllegalStateException("BigQuery unavailable"));

        mvc.perform(post("/internal/reload").header("X-Api-Key", ADMIN_KEY))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
