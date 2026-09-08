package com.tanidikvar.api.management.entity;
import java.util.UUID;
public record ManagementUserDetail(ManagedUser user,String universityName,String departmentName,Integer graduationYear,UUID avatarFileId,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID verificationId,long questions,long communityAnswers,long adminAnswers) {}
