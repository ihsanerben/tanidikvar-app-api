package com.tanidikvar.api.verification.dto;import jakarta.validation.constraints.*;public record SchoolEmailRequest(@NotBlank @Email @Size(max=254) String email){}
