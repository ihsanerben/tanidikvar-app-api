package com.tanidikvar.api.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ManagementContentEditRequest(
        @NotBlank @Size(min = 10, max = 5000) String body,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull @PositiveOrZero Long version
) { }
