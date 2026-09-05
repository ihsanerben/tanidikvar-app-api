package com.tanidikvar.api.profile.entity;
import java.time.Instant;
import java.util.UUID;
public record UserProfile(UUID userId, String firstName, String lastName, EducationStatus educationStatus,
        UUID universityDepartmentId, Integer graduationYear, String biography, String occupation, String company,
        Instant deletedAt, long version) { }
