package com.tanidikvar.api.auth.security;

import com.tanidikvar.api.auth.exception.AuthRejectedException;
import com.tanidikvar.api.auth.service.AuthenticationService;
import com.tanidikvar.api.common.error.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class CookieAuthenticationFilter extends OncePerRequestFilter {
    private final AuthenticationService authentication;
    private final AuthCookies cookies;
    private final ObjectMapper mapper;
    public CookieAuthenticationFilter(AuthenticationService authentication, AuthCookies cookies, ObjectMapper mapper) {
        this.authentication = authentication; this.cookies = cookies; this.mapper = mapper;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/auth/")
                || request.getRequestURI().equals("/api/health");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = cookies.read(request, AuthCookies.ACCESS);
        if (token != null) {
            try {
                var principal = authentication.authenticate(token);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.user().role()))));
                SecurityContextHolder.setContext(context);
            } catch (AuthRejectedException ignored) { SecurityContextHolder.clearContext(); }
            catch (org.springframework.dao.DataAccessException | org.springframework.transaction.CannotCreateTransactionException e) {
                response.setStatus(503); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(mapper.writeValueAsString(ApiErrors.create(request, 503, "SERVICE_UNAVAILABLE", "Hizmete şu anda ulaşılamıyor.")));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
