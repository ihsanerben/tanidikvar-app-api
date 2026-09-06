package com.tanidikvar.api.management.dto;
import java.util.*;
import com.tanidikvar.api.question.entity.QuestionScope;
public record ClassificationResponse(QuestionScope scope,UUID universityId,UUID universityDepartmentId,List<UUID> tagIds,long version,com.tanidikvar.api.catalog.dto.EducationResponse education) {}
