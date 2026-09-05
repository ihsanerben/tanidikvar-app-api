package com.tanidikvar.api.question.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record QuestionUpdateRequest(@NotNull @PositiveOrZero Long version, @NotNull @Valid QuestionContent content) { }
