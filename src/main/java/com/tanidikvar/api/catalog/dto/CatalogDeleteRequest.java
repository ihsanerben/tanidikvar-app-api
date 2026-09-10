package com.tanidikvar.api.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CatalogDeleteRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(max=1000) String reason) { }
