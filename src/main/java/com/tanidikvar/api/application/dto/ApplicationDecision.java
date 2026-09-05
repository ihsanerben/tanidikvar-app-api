package com.tanidikvar.api.application.dto;
import jakarta.validation.constraints.*;
public record ApplicationDecision(@NotNull @Pattern(regexp="APPROVED|REJECTED") String status,@Size(max=1000) String reason,@Min(0) long version) {}

