package com.tanidikvar.api.auth.dto;
import jakarta.validation.constraints.*;
public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) { }
