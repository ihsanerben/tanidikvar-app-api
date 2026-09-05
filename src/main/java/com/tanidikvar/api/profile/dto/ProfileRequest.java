package com.tanidikvar.api.profile.dto;
import com.tanidikvar.api.profile.entity.EducationStatus;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ProfileRequest(@NotBlank @Size(max=80) String firstName, @NotBlank @Size(max=80) String lastName,
        @NotNull EducationStatus educationStatus, UUID universityDepartmentId, Integer graduationYear,
        @Size(max=1000) String biography, @Size(max=120) String occupation, @Size(max=120) String company,
        @NotNull @PositiveOrZero Long version) { }
