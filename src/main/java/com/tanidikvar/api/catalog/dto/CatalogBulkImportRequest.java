package com.tanidikvar.api.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CatalogBulkImportRequest(
        @NotNull @Size(max=500) List<@NotBlank @Size(max=200) String> universities,
        @NotNull @Size(max=1000) List<@NotBlank @Size(max=200) String> departments,
        @NotNull @Size(max=5000) List<@Valid EducationPair> matches,
        @NotBlank @Size(max=1000) String reason) {
    public record EducationPair(@NotBlank @Size(max=200) String university,
                                @NotBlank @Size(max=200) String department) { }
}
