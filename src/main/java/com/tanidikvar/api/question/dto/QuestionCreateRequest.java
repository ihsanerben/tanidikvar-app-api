package com.tanidikvar.api.question.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record QuestionCreateRequest(@NotNull UUID requestId, @NotNull @Valid QuestionContent content) { }
