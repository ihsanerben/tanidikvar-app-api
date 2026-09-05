package com.tanidikvar.api.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String secret, Duration accessTtl, Duration refreshTtl) {
    public AuthProperties {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("JWT_SECRET is required");
        if (accessTtl == null || accessTtl.isNegative() || accessTtl.compareTo(Duration.ofSeconds(30)) < 0
                || accessTtl.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Access TTL must be between 30 seconds and 1 hour");
        if (refreshTtl == null || refreshTtl.compareTo(accessTtl) <= 0 || refreshTtl.compareTo(Duration.ofDays(30)) > 0)
            throw new IllegalArgumentException("Refresh TTL must exceed access TTL and be at most 30 days");
    }
    @Override public String toString() { return "AuthProperties[REDACTED]"; }
}
