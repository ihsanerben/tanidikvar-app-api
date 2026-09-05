package com.tanidikvar.api.answer.dto;
import jakarta.validation.constraints.*;
public record AnswerUpdateRequest(@NotBlank @Size(min=10,max=5000) String body, @NotNull @PositiveOrZero Long version) { }
