package com.tanidikvar.api.management.dto;
import jakarta.validation.constraints.*;
public record ManagerAccountRequest(@NotBlank @Size(max=80) String firstName,@NotBlank @Size(max=80) String lastName,@NotNull @PositiveOrZero Long version) {}
