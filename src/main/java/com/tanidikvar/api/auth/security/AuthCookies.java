package com.tanidikvar.api.auth.security;

import com.tanidikvar.api.auth.service.IssuedSession;
import jakarta.servlet.http.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    public static final String ACCESS = "TV_ACCESS";
    public static final String REFRESH = "TV_REFRESH";
    private final boolean secure;
    private final Clock clock;
    public AuthCookies(@Value("${app.secure-cookies}") boolean secure, Clock clock) { this.secure = secure; this.clock = clock; }
    public String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public void write(HttpServletResponse response, IssuedSession session) {
        set(response, ACCESS, session.accessToken(), "/", Duration.between(clock.instant(), session.accessExpiresAt()));
        set(response, REFRESH, session.refreshToken(), "/api/auth", Duration.between(clock.instant(), session.refreshExpiresAt()));
    }
    public void clear(HttpServletResponse response) {
        set(response, ACCESS, "", "/", Duration.ZERO); set(response, REFRESH, "", "/api/auth", Duration.ZERO);
    }
    private void set(HttpServletResponse response, String name, String value, String path, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true).secure(secure)
                .sameSite("Lax").path(path).maxAge(age.isNegative() ? Duration.ZERO : age).build().toString());
    }
}
