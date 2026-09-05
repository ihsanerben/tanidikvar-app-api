package com.tanidikvar.api.question.dto;
import com.tanidikvar.api.question.entity.QuestionScope;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record QuestionContent(@NotBlank @Size(min=10,max=200) String title, @Size(max=5000) String body,
        @NotNull QuestionScope scope, UUID universityId, UUID universityDepartmentId,
        @NotNull @Size(max=5) List<@NotNull UUID> tagIds) { }
