package com.tanidikvar.api.auth.dto;
import jakarta.validation.constraints.*;
public record ResetPasswordRequest(@NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 10, max = 72) String password) { }
