package com.tanidikvar.api.profile.entity;
import java.util.UUID;
public record PublicAdminProfile(UUID id,String name,boolean activeAdmin,String universityName,String departmentName,String educationStatus,Integer graduationYear,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID avatarFileId,long answerCount,long communityAnswerCount) {}
