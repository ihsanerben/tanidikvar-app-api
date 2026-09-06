package com.tanidikvar.api.catalog.dto;
import jakarta.validation.constraints.*;
public record CatalogStatusRequest(@NotNull Boolean deleted, @NotNull @PositiveOrZero Long version, @jakarta.validation.constraints.Size(max=1000) String reason) { }
