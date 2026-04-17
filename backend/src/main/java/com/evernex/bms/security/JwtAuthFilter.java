package com.evernex.bms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the express authRequired + loadUserFactories middleware.
 * Every request under /api/v1 needs a Bearer JWT, except for auth/login, auth/signup, and /health.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/health",
        "/api/v1/auth/login",
        "/api/v1/auth/signup"
    );

    private final JwtUtil jwt;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JwtAuthFilter(JwtUtil jwt, JdbcTemplate jdbc) {
        this.jwt = jwt;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (!path.startsWith("/api/v1") || PUBLIC_PATHS.contains(path) || "OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) {
            writeError(res, 401, "인증이 필요합니다.");
            return;
        }

        Claims claims;
        try {
            claims = jwt.verify(token);
        } catch (Exception e) {
            writeError(res, 401, "토큰이 유효하지 않습니다.");
            return;
        }

        long uid = ((Number) claims.get("uid")).longValue();
        String role = String.valueOf(claims.get("role"));
        String email = String.valueOf(claims.get("email"));
        String name = String.valueOf(claims.get("name"));

        List<Long> allowed;
        boolean adminScope = "admin".equals(role);
        try {
            if (adminScope) {
                allowed = jdbc.query("SELECT factory_id FROM factories WHERE is_active=1",
                    (rs, i) -> rs.getLong("factory_id"));
            } else {
                allowed = jdbc.query("SELECT factory_id FROM user_factories WHERE user_id=?",
                    (rs, i) -> rs.getLong("factory_id"), uid);
            }
        } catch (Exception e) {
            writeError(res, 500, "공장 권한 조회 실패");
            return;
        }

        AuthContext.set(new AuthPrincipal(uid, role, email, name, allowed, adminScope));
        try {
            chain.doFilter(req, res);
        } finally {
            AuthContext.clear();
        }
    }

    private void writeError(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(), Map.of("error", msg));
    }
}
