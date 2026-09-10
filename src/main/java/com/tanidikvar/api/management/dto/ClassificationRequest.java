package com.tanidikvar.api.management.dto;
import java.util.*;
import com.tanidikvar.api.question.entity.QuestionScope;
import jakarta.validation.constraints.*;
public record ClassificationRequest(@NotNull QuestionScope scope,UUID universityId,UUID departmentId,@NotNull @Size(max=5) List<@NotNull UUID> tagIds,@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=1000) String reason,@Size(min=10,max=200) String title,@Size(max=5000) String body) {}
