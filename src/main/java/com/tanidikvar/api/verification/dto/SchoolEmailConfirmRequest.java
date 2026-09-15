package com.tanidikvar.api.verification.dto;import jakarta.validation.constraints.*;public record SchoolEmailConfirmRequest(@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
