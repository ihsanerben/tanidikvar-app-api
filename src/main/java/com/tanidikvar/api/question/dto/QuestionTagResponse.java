package com.tanidikvar.api.question.dto;
import java.util.UUID;
public record QuestionTagResponse(UUID id, String name, boolean available) { }
