package com.tanidikvar.api.application.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ApplicationSubmission(@NotNull UUID requestId,@Min(1) long profileVersion) {}

