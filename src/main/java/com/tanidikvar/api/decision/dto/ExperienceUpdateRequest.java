package com.tanidikvar.api.decision.dto;
import jakarta.validation.constraints.*;
public record ExperienceUpdateRequest(@NotBlank @Size(min=10,max=200) String title,@NotBlank @Size(min=20,max=5000) String body,@NotNull @Min(0) Long version) {}
