package com.tanidikvar.api.auth.service;

import com.tanidikvar.api.auth.dto.CurrentUserResponse;
import java.time.Instant;

/** Internal result; tokens must never be serialized in an HTTP body or logged. */
public record IssuedSession(CurrentUserResponse user, String accessToken, String refreshToken,
        Instant accessExpiresAt, Instant refreshExpiresAt) {
    @Override public String toString() { return "IssuedSession[REDACTED]"; }
}
