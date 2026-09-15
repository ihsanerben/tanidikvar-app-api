package com.tanidikvar.api.decision.dto;
import jakarta.validation.constraints.*;import java.util.UUID;
public record GraduateOutcomeRequest(@NotNull UUID universityId,@NotNull UUID programId,@NotBlank @Size(min=2,max=120) String sector,@NotBlank @Size(min=2,max=120) String firstRole,@NotBlank @Size(min=2,max=80) String companyType,boolean graduateStudy,@Min(0) @Max(120) int jobSearchMonths) {}
