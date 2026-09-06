package com.tanidikvar.api.catalog.dto;
import jakarta.validation.constraints.*;
public record CatalogUpdateRequest(@NotBlank @Size(max=200) String name, @NotNull @PositiveOrZero Long version, @jakarta.validation.constraints.Size(max=1000) String reason) { }
