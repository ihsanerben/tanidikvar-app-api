package com.tanidikvar.api.profile.entity;
import java.util.UUID;
public record PublicProfile(UUID id,String name,String email,String role,String educationStatus,String universityName,String departmentName,Integer graduationYear,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID avatarFileId) {}
