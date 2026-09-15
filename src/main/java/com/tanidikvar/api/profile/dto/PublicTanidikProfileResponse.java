package com.tanidikvar.api.profile.dto;
import java.time.Instant;import java.util.UUID;
public record PublicTanidikProfileResponse(UUID id,String name,boolean activeTanidik,boolean educationVerified,String universityName,String departmentName,String educationStatus,Integer classYear,Integer graduationYear,String biography,String occupation,String company,String linkedinUrl,String portfolioUrl,UUID avatarFileId,long tanidikAnswerCount,long communityAnswerCount,long helpfulVoteCount,long bestAnswerCount,long helpedPeopleCount,Instant createdAt) {}
