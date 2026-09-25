package com.tanidikvar.api.auth.dto;

import jakarta.validation.constraints.*;

public record RefreshSessionRequest(@NotBlank @Size(max = 4096) String refreshToken) {
    @Override public String toString() { return "RefreshSessionRequest[REDACTED]"; }
}
