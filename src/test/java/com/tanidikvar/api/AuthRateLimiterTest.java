package com.tanidikvar.api;

import com.tanidikvar.api.auth.security.AuthRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimiterTest {
    @Test void appliesEndpointSpecificPoliciesWithoutSharingWindows() {
        var limiter=new AuthRateLimiter(Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        for(int i=0;i<30;i++) assertThat(limiter.retryAfter("127.0.0.1","question-create")).isZero();
        assertThat(limiter.retryAfter("127.0.0.1","question-create")).isPositive();
        assertThat(limiter.retryAfter("127.0.0.1","answer-create")).isZero();
        for(int i=0;i<60;i++) assertThat(limiter.retryAfter("127.0.0.1","refresh")).isZero();
        assertThat(limiter.retryAfter("127.0.0.1","refresh")).isPositive();
    }
}
