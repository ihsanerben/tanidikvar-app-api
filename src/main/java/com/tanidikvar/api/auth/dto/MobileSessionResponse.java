package com.tanidikvar.api.auth.dto;

import java.time.Instant;
import jakarta.validation.constraints.NotNull;

public record MobileSessionResponse(@NotNull CurrentUserResponse user, @NotNull String accessToken, @NotNull String refreshToken,
        @NotNull Instant accessExpiresAt, @NotNull Instant refreshExpiresAt) {
    @Override public String toString() { return "MobileSessionResponse[REDACTED]"; }
}
