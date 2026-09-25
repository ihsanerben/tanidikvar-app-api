package com.tanidikvar.api.auth.dto;

import jakarta.validation.constraints.*;

public record CloseAccountRequest(@NotBlank @Size(max = 72) String password) {
    @Override public String toString() { return "CloseAccountRequest[REDACTED]"; }
}
