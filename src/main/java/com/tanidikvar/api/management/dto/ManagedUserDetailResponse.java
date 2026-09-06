package com.tanidikvar.api.management.dto;
import java.util.UUID;
public record ManagedUserDetailResponse(ManagedUserResponse user,String universityName,String departmentName,Integer graduationYear,UUID verificationId,long questions,long communityAnswers,long adminAnswers) {}
