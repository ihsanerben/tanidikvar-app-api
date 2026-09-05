package com.tanidikvar.api.answer.dto;
import jakarta.validation.constraints.*;
public record AnswerCreateRequest(@NotBlank @Size(min=10,max=5000) String body) { }
