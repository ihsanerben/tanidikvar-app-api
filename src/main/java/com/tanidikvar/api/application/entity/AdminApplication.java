package com.tanidikvar.api.application.entity;
import java.time.Instant;
import java.util.UUID;
public record AdminApplication(UUID id,UUID applicantId,UUID requestId,String firstName,String lastName,String educationStatus,UUID universityDepartmentId,String universityName,String departmentName,Integer graduationYear,String occupation,String company,UUID documentFileId,String documentSha256,long profileVersion,String status,Instant submittedAt,UUID reviewedBy,Instant reviewedAt,String rejectionReason,long version,boolean activeVerification) {}

