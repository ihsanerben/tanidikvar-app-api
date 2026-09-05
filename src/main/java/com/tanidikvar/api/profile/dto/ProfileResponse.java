package com.tanidikvar.api.profile.dto;
import com.tanidikvar.api.catalog.dto.EducationResponse;
import com.tanidikvar.api.profile.entity.EducationStatus;
public record ProfileResponse(String firstName, String lastName, EducationStatus educationStatus,
        EducationResponse education, Integer graduationYear, String biography, String occupation, String company,
        boolean completed, long version) { }
