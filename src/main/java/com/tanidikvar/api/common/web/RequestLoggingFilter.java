package com.tanidikvar.api.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        try { chain.doFilter(request, response); }
        finally {
            // Query strings, headers and bodies may contain secrets and are never logged.
            String path = request.getRequestURI().replaceAll("[\r\n\t]", "_");
            log.info("http_request method={} path={} status={} durationMs={} requestId={}",
                    request.getMethod(), path, response.getStatus(), (System.nanoTime() - start) / 1_000_000, requestId);
            MDC.remove("requestId");
        }
    }
}
