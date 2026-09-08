package com.tanidikvar.api.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TagBulkImportRequest(
        @NotNull @Size(min=1,max=500) List<@NotBlank @Size(max=200) String> tags,
        @NotBlank @Size(max=1000) String reason) { }
