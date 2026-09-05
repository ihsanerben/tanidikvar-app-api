package com.tanidikvar.api.management.dto;
import jakarta.validation.constraints.*;
public record ManagementStatusRequest(@NotNull Boolean hidden,@Min(0) long version,@NotBlank @Size(max=1000) String reason) {}
