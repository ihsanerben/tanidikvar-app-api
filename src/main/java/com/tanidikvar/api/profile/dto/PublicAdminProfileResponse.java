package com.tanidikvar.api.profile.dto;
import java.util.UUID;
public record PublicAdminProfileResponse(UUID id,String name,boolean activeAdmin,String universityName,String departmentName,String educationStatus,Integer graduationYear,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID avatarFileId,long answerCount) {}

