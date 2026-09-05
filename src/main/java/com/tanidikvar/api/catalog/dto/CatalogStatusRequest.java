package com.tanidikvar.api.catalog.dto;
import jakarta.validation.constraints.*;
public record CatalogStatusRequest(@NotNull Boolean deleted, @NotNull @PositiveOrZero Long version) { }
