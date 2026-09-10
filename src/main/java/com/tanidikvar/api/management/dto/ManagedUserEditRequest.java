package com.tanidikvar.api.management.dto;
import com.tanidikvar.api.profile.entity.EducationStatus;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ManagedUserEditRequest(@NotBlank @Size(max=80) String firstName,@NotBlank @Size(max=80) String lastName,@NotNull EducationStatus educationStatus,UUID universityId,UUID departmentId,Integer graduationYear,@Size(max=2000) String biography,@Size(max=120) String occupation,@Size(max=120) String company,@Size(max=500) String linkedinUrl,@Size(max=500) String portfolioUrl) {}
