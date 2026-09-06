package com.tanidikvar.api.management.entity;
import java.util.*;
import com.tanidikvar.api.question.entity.QuestionScope;
public record ManagementClassification(QuestionScope scope,UUID universityId,UUID universityDepartmentId,List<UUID> tagIds,long version) {}
