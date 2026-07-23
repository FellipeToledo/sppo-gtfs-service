package com.fajtech.sppogtfs.infrastructure.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Optional API-key gate (§7). Two independent keys:
 * <ul>
 *   <li>{@code sppo.gtfs.api.api-key} guards {@code /api/**} (empty = public).</li>
 *   <li>{@code sppo.gtfs.api.admin-key} guards {@code /internal/**}
 *       (empty = falls back to api-key; if both empty, /internal is closed).</li>
 * </ul>
 * Actuator endpoints are always open (network-restricted in deployment).
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";

    private final String apiKey;
    private final String adminKey;
    private final ObjectMapper mapper;

    public ApiKeyFilter(String apiKey, String adminKey, ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.adminKey = adminKey == null ? "" : adminKey;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/") || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String provided = request.getHeader(HEADER);

        if (path.startsWith("/internal/")) {
            String required = !adminKey.isBlank() ? adminKey : apiKey;
            // /internal is never public: if no key is configured at all, deny.
            if (required.isBlank() || !required.equals(provided)) {
                deny(request, response);
                return;
            }
        } else if (!apiKey.isBlank() && !apiKey.equals(provided)) {
            deny(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void deny(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = Map.of(
                "type", "about:blank",
                "title", "Unauthorized",
                "status", 401,
                "detail", "Missing or invalid API key",
                "instance", request.getRequestURI());
        mapper.writeValue(response.getWriter(), body);
    }
}
