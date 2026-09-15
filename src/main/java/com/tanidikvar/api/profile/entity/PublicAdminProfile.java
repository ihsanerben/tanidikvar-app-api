package com.tanidikvar.api.profile.entity;
import java.time.Instant;
import java.util.UUID;
public record PublicAdminProfile(UUID id,String name,boolean activeAdmin,boolean educationVerified,String universityName,String departmentName,String educationStatus,Integer classYear,Integer graduationYear,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID avatarFileId,long answerCount,long communityAnswerCount,long helpfulVoteCount,long bestAnswerCount,long helpedPeopleCount,Instant createdAt) {}
