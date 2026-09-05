package com.tanidikvar.api.catalog.dto;
import jakarta.validation.constraints.*;
public record CatalogUpdateRequest(@NotBlank @Size(max=200) String name, @NotNull @PositiveOrZero Long version) { }
