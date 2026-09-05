package com.tanidikvar.api.question.dto;
import jakarta.validation.constraints.*;
public record QuestionArchiveRequest(@NotNull @PositiveOrZero Long version) { }
