package com.tanidikvar.api.management.dto;
import com.tanidikvar.api.common.dto.PageResponse;
public record ManagedQuestionResponse(ManagedContentResponse question,ClassificationResponse classification,PageResponse<ManagedContentResponse> answers) {}
