package com.tanidikvar.api.auth.dto;
import jakarta.validation.constraints.*;
public record TokenRequest(@NotBlank @Size(max = 128) String token) { }
