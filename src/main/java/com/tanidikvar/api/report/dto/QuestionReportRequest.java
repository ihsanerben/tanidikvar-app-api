package com.tanidikvar.api.report.dto;
import jakarta.validation.constraints.*;
public record QuestionReportRequest(@NotBlank @Size(min=10,max=1000) String reason) {}
