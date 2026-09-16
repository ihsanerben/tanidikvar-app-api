package com.tanidikvar.api.profile.dto;
import com.tanidikvar.api.catalog.dto.EducationResponse;
import com.tanidikvar.api.profile.entity.EducationStatus;
public record ProfileResponse(String firstName, String lastName, EducationStatus educationStatus, java.util.UUID programId,
        EducationResponse education, Integer classYear, Integer graduationYear, String biography, String occupation, String company, String linkedinUrl, String portfolioUrl,
        boolean completed, long version) { }
