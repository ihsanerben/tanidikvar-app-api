package com.tanidikvar.api.auth.security;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Bounded, single-instance limiter. Uses the socket address, never untrusted forwarding headers. */
@Component
public class AuthRateLimiter {
    private record Window(Instant expiresAt, int count) { }
    private record Policy(int limit, Duration period) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthRateLimiter(Clock clock) { this.clock = clock; }
    public synchronized long retryAfter(String address, String operation) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        String key = address + ":" + operation;
        Policy policy=policy(operation);
        Window old = windows.get(key);
        if (old == null && windows.size() >= 10000) return 60;
        if (old != null && old.count() >= policy.limit()) return Math.max(1, Duration.between(now, old.expiresAt()).toSeconds() + 1);
        windows.put(key, new Window(old == null ? now.plus(policy.period()) : old.expiresAt(), old == null ? 1 : old.count() + 1));
        return 0;
    }
    private Policy policy(String operation) {
        return switch(operation) {
            case "refresh" -> new Policy(60,Duration.ofMinutes(1));
            case "question-create" -> new Policy(30,Duration.ofMinutes(15));
            case "answer-create", "admin-answer-create" -> new Policy(60,Duration.ofMinutes(15));
            case "interaction", "read" -> new Policy(240,Duration.ofMinutes(1));
            case "manager-write" -> new Policy(120,Duration.ofMinutes(15));
            default -> new Policy(10,Duration.ofMinutes(15));
        };
    }
}
