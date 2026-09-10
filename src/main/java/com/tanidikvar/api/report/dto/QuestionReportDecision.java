package com.tanidikvar.api.report.dto;
import jakarta.validation.constraints.*;
public record QuestionReportDecision(@NotBlank @Pattern(regexp="RESOLVED|DISMISSED") String status,@NotBlank @Size(max=1000) String reason,long version) {}
