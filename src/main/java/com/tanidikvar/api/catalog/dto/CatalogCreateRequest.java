package com.tanidikvar.api.catalog.dto;
import jakarta.validation.constraints.*;
public record CatalogCreateRequest(@NotBlank @Size(max=200) String name, @jakarta.validation.constraints.Size(max=1000) String reason) { }
