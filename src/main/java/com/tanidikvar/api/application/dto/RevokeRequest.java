package com.tanidikvar.api.application.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record RevokeRequest(@NotNull UUID verificationId,@NotBlank @Size(max=1000) String reason) {}

