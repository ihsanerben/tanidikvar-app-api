package com.tanidikvar.api.question.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.question.dto.*;
import com.tanidikvar.api.question.entity.QuestionScope;
import com.tanidikvar.api.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class QuestionController {
    private final QuestionService questions;
    public QuestionController(QuestionService questions) { this.questions=questions; }
    @GetMapping("/api/questions")
    @Operation(summary="Aktif soruları en yeniden eskiye listeler")
    public PageResponse<QuestionResponse> list(@RequestParam(required=false) QuestionScope scope,@RequestParam(required=false) UUID universityId,
            @RequestParam(required=false) UUID universityDepartmentId,@RequestParam(required=false) UUID tagId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return questions.list(null,scope,universityId,universityDepartmentId,tagId,page,size);
    }
    @GetMapping("/api/me/questions") @SecurityRequirement(name="accessCookie")
    @Operation(summary="Kendi sorularını arşivdekiler dahil listeler")
    public PageResponse<QuestionResponse> mine(@AuthenticationPrincipal SessionPrincipal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return questions.list(principal.userId(),null,null,null,null,page,size);
    }
    @GetMapping("/api/questions/{id}")
    @Operation(summary="Soruyu okur; arşiv görünür, soft-deleted soru 404 döner")
    public QuestionResponse get(@PathVariable UUID id) { return questions.get(id); }
    @PostMapping("/api/questions") @ResponseStatus(HttpStatus.CREATED) @SecurityRequirement(name="accessCookie")
    @Operation(summary="Profilini tamamlayan kullanıcı soru oluşturur; requestId tekrarları aynı soruyu döndürür")
    public QuestionResponse create(@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody QuestionCreateRequest request) { return questions.create(principal.userId(),request); }
    @PutMapping("/api/questions/{id}") @SecurityRequirement(name="accessCookie")
    public QuestionResponse update(@AuthenticationPrincipal SessionPrincipal principal,@PathVariable UUID id,@Valid @RequestBody QuestionUpdateRequest request) { return questions.update(principal.userId(),id,request); }
    @PostMapping("/api/questions/{id}/archive") @SecurityRequirement(name="accessCookie")
    public QuestionResponse archive(@AuthenticationPrincipal SessionPrincipal principal,@PathVariable UUID id,@Valid @RequestBody QuestionArchiveRequest request) { return questions.archive(principal.userId(),id,request); }
}
