package com.tanidikvar.api.answer.controller;
import com.tanidikvar.api.answer.dto.*;
import com.tanidikvar.api.answer.service.AnswerService;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class AnswerController {
    private final AnswerService answers;
    public AnswerController(AnswerService answers) {this.answers=answers;}
    @GetMapping("/api/me/answers") @SecurityRequirement(name="accessCookie")
    @Operation(summary="Kendi topluluk cevaplarını, kaldırılmış olanlar dahil, okunabilir sorularla listeler")
    public PageResponse<OwnAnswerResponse> history(@AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return answers.listMine(principal.userId(),page,size);
    }
    @GetMapping("/api/questions/{id}/answers")
    @Operation(summary="Görünür topluluk cevaplarını ilk yayın sırasıyla listeler")
    public PageResponse<AnswerResponse> list(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return answers.list(id,page,size);}
    @GetMapping("/api/questions/{id}/my-answer") @SecurityRequirement(name="accessCookie")
    @Operation(summary="Kendi topluluk cevabını kaldırılmış olsa da getirir; yoksa 204")
    public ResponseEntity<AnswerResponse> mine(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal) {
        return answers.mine(id,principal.userId()).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.noContent().build());
    }
    @PostMapping("/api/questions/{id}/answers") @ResponseStatus(HttpStatus.CREATED) @SecurityRequirement(name="accessCookie")
    public AnswerResponse create(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody AnswerCreateRequest request) {return answers.create(id,principal.userId(),request);}
    @PutMapping("/api/answers/{id}") @SecurityRequirement(name="accessCookie")
    public AnswerResponse update(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody AnswerUpdateRequest request) {return answers.update(id,principal.userId(),request);}
    @PutMapping("/api/answers/{id}/status") @SecurityRequirement(name="accessCookie")
    @Operation(summary="Sahibi cevabını soft delete eder veya aktif soruda aynı kaydı geri yükler")
    public AnswerResponse status(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody AnswerStatusRequest request) {return answers.status(id,principal.userId(),request);}
}
