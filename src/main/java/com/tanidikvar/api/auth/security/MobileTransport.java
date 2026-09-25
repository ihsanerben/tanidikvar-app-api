package com.tanidikvar.api.auth.security;

import jakarta.servlet.http.HttpServletRequest;

/** CSRF exemption is limited to requests that cannot authenticate via ambient cookies. */
public final class MobileTransport {
    private MobileTransport() { }
    public static boolean cookieFree(HttpServletRequest request) {
        if (request.getCookies() != null)
            for (var cookie : request.getCookies())
                if (AuthCookies.ACCESS.equals(cookie.getName()) || AuthCookies.REFRESH.equals(cookie.getName())) return false;
        return true;
    }
    public static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
    public static boolean csrfExempt(HttpServletRequest request) {
        if (!cookieFree(request)) return false;
        if (request.getRequestURI().startsWith("/api/auth/mobile/"))
            return request.getContentType() != null &&
                    request.getContentType().split(";")[0].trim().equalsIgnoreCase("application/json");
        return request.getRequestURI().startsWith("/api/") && bearer(request) != null;
    }
}
