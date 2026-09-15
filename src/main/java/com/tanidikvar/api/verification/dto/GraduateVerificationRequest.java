package com.tanidikvar.api.verification.dto;import jakarta.validation.constraints.*;public record GraduateVerificationRequest(@NotBlank@Size(min=20,max=2000)String evidence){}
