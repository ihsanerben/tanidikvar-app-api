package com.tanidikvar.api.application.dto;
import java.time.Instant;
import java.util.UUID;
public record ApplicationResponse(UUID id,UUID applicantId,String firstName,String lastName,String educationStatus,String universityName,String departmentName,Integer graduationYear,String occupation,String company,UUID documentFileId,String status,Instant submittedAt,UUID reviewedBy,Instant reviewedAt,String rejectionReason,long version,boolean activeVerification) {}

