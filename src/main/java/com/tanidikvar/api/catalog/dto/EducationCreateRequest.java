package com.tanidikvar.api.catalog.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record EducationCreateRequest(@NotNull UUID universityId, @NotNull UUID departmentId, @jakarta.validation.constraints.Size(max=1000) String reason) { }
