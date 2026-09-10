package com.tanidikvar.api.management.dto;
import jakarta.validation.constraints.*;
public record ManagedUserEditRequest(@NotBlank @Size(max=80) String firstName,@NotBlank @Size(max=80) String lastName,@Size(max=2000) String biography,@Size(max=120) String occupation,@Size(max=120) String company,@Size(max=500) String linkedinUrl,@Size(max=500) String portfolioUrl) {}
