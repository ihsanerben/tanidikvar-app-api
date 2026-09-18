package com.tanidikvar.api.auth.security;

import com.tanidikvar.api.common.error.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final java.util.Set<String> AUTH_OPERATIONS = java.util.Set.of("register", "login", "refresh",
            "resend-verification", "verify-email", "forgot-password", "reset-password");
    private final AuthRateLimiter limiter;
    private final ClientAddressResolver addresses;
    private final ObjectMapper mapper;
    public AuthRateLimitFilter(AuthRateLimiter limiter, ClientAddressResolver addresses, ObjectMapper mapper) { this.limiter = limiter; this.addresses = addresses; this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String operation=operation(request);
        if (operation!=null) {
            long retry = limiter.retryAfter(addresses.resolve(request), operation);
            if (retry > 0) {
                response.setStatus(429); response.setHeader("Retry-After", Long.toString(retry));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(mapper.writeValueAsString(ApiErrors.create(request, 429, "RATE_LIMITED", "Çok fazla deneme yaptın. Biraz bekleyip tekrar dene.")));
                return;
            }
        }
        chain.doFilter(request, response);
    }
    private String operation(HttpServletRequest request) {
        String method=request.getMethod(),path=request.getRequestURI();
        if(method.equals("POST") && path.startsWith("/api/auth/")) {
            String operation=path.substring("/api/auth/".length());
            return AUTH_OPERATIONS.contains(operation)?operation:null;
        }
        if(method.equals("POST") && path.equals("/api/questions"))return "question-create";
        if(method.equals("POST") && path.equals("/api/contact"))return "contact";
        if(method.equals("POST") && path.matches("/api/questions/[^/]+/answers"))return "answer-create";
        if(method.equals("POST") && path.matches("/api/questions/[^/]+/admin-answers"))return "admin-answer-create";
        if((method.equals("PUT") && path.matches("/api/questions/[^/]+/like")) || (method.equals("POST") && path.matches("/api/questions/[^/]+/views")))return "interaction";
        if(method.equals("POST") && path.startsWith("/api/me/education-verification/"))return "verification";
        if(method.equals("POST") && path.matches("/api/questions/[^/]+/reports"))return "report";
        if(!method.equals("GET") && path.startsWith("/api/manager/"))return "manager-write";
        if(!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS"))return "content-write";
        if(method.equals("GET") && (path.equals("/api/questions") || path.equals("/api/popular") || path.equals("/api/universities") || path.equals("/api/departments") || path.equals("/api/tags") || path.equals("/api/admins")))return "read";
        return null;
    }
}
