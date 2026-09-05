package com.tanidikvar.api.auth.security;

import com.tanidikvar.api.common.error.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final java.util.Set<String> OPERATIONS = java.util.Set.of("register", "login", "refresh",
            "resend-verification", "verify-email", "forgot-password", "reset-password");
    private final AuthRateLimiter limiter;
    private final ObjectMapper mapper;
    public AuthRateLimitFilter(AuthRateLimiter limiter, ObjectMapper mapper) { this.limiter = limiter; this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String operation = path.startsWith("/api/auth/") ? path.substring("/api/auth/".length()) : "";
        if (request.getMethod().equals("POST") && OPERATIONS.contains(operation)) {
            long retry = limiter.retryAfter(request.getRemoteAddr(), operation);
            if (retry > 0) {
                response.setStatus(429); response.setHeader("Retry-After", Long.toString(retry));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(mapper.writeValueAsString(ApiErrors.create(request, 429, "RATE_LIMITED", "Çok fazla deneme yaptın. Biraz bekleyip tekrar dene.")));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
