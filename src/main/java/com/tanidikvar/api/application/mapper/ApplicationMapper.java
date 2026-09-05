package com.tanidikvar.api.application.mapper;
import com.tanidikvar.api.application.entity.AdminApplication;
import com.tanidikvar.api.application.dto.ApplicationResponse;
import org.springframework.stereotype.Component;
@Component
public class ApplicationMapper {
 public ApplicationResponse toResponse(AdminApplication a){return new ApplicationResponse(a.id(),a.applicantId(),a.firstName(),a.lastName(),a.educationStatus(),a.universityName(),a.departmentName(),a.graduationYear(),a.occupation(),a.company(),a.documentFileId(),a.status(),a.submittedAt(),a.reviewedBy(),a.reviewedAt(),a.rejectionReason(),a.version(),a.activeVerification());}
}

