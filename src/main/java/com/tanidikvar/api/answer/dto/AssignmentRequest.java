package com.tanidikvar.api.answer.dto;
import jakarta.validation.constraints.*;
public record AssignmentRequest(@NotNull Boolean assigned,@NotNull @PositiveOrZero Long version) {}

