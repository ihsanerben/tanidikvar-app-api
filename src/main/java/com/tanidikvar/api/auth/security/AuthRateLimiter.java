package com.tanidikvar.api.auth.security;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Bounded, single-instance limiter. Uses the socket address, never untrusted forwarding headers. */
@Component
public class AuthRateLimiter {
    private record Window(Instant expiresAt, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthRateLimiter(Clock clock) { this.clock = clock; }
    public synchronized long retryAfter(String address, String operation) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        String key = address + ":" + operation;
        int limit = operation.equals("refresh") ? 60 : 10;
        Duration period = operation.equals("refresh") ? Duration.ofMinutes(1) : Duration.ofMinutes(15);
        Window old = windows.get(key);
        if (old == null && windows.size() >= 10000) return 60;
        if (old != null && old.count() >= limit) return Math.max(1, Duration.between(now, old.expiresAt()).toSeconds() + 1);
        windows.put(key, new Window(old == null ? now.plus(period) : old.expiresAt(), old == null ? 1 : old.count() + 1));
        return 0;
    }
}
