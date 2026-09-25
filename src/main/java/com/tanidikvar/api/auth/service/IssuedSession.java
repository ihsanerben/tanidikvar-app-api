package com.tanidikvar.api.auth.service;

import com.tanidikvar.api.auth.dto.CurrentUserResponse;
import java.time.Instant;

/** Internal result; only MobileSessionMapper may expose tokens to the mobile transport. Never log. */
public record IssuedSession(CurrentUserResponse user, String accessToken, String refreshToken,
        Instant accessExpiresAt, Instant refreshExpiresAt) {
    @Override public String toString() { return "IssuedSession[REDACTED]"; }
}
