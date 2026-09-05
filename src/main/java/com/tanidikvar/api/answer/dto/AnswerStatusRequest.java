package com.tanidikvar.api.answer.dto;
import jakarta.validation.constraints.*;
public record AnswerStatusRequest(@NotNull Boolean deleted, @NotNull @PositiveOrZero Long version) { }
