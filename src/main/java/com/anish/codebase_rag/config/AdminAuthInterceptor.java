package com.anish.codebase_rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Gates the mutating endpoints (/api/ingest, /api/ingest/github, /api/clear)
 * behind a shared secret so a public deployment can be queried by anyone but
 * only ingested/cleared by the owner. Controlled by app.admin-key
 * (ADMIN_KEY env var); if unset/blank, every request is allowed through --
 * that's the local-dev default and must not be the case in production.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Admin-Key";
    private final String adminKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuthInterceptor(@Value("${app.admin-key:}") String adminKey) {
        this.adminKey = adminKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (adminKey == null || adminKey.isBlank()) {
            return true; // no key configured -- local dev, don't lock the owner out
        }

        String provided = request.getHeader(HEADER);
        if (adminKey.equals(provided)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
            Map.of("message", "Missing or invalid " + HEADER + " header")));
        return false;
    }
}
