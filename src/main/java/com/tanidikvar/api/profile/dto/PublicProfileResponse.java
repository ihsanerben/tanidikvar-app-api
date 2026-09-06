package com.tanidikvar.api.profile.dto;
import java.util.UUID;
public record PublicProfileResponse(UUID id,String name,String email,String role,String educationStatus,String universityName,String departmentName,Integer graduationYear,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID avatarFileId) {}
