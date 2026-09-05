package com.tanidikvar.api.answer.controller;
import com.tanidikvar.api.answer.dto.*;
import com.tanidikvar.api.answer.service.AdminAnswerService;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class AdminAnswerController {
 private final AdminAnswerService answers;
 public AdminAnswerController(AdminAnswerService answers){this.answers=answers;}
 @GetMapping("/api/questions/{id}/admin-answers")
 public PageResponse<AdminAnswerResponse> list(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return answers.list(id,page,size);}
 @GetMapping("/api/questions/{id}/my-admin-answer") @SecurityRequirement(name="accessCookie")
 public OwnAdminAnswerResponse own(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p){return answers.own(id,p.userId());}
 @PostMapping("/api/questions/{id}/admin-answers") @ResponseStatus(HttpStatus.CREATED) @SecurityRequirement(name="accessCookie")
 public AdminAnswerResponse create(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestBody AnswerCreateRequest request){return answers.create(id,p.userId(),request);}
 @PutMapping("/api/questions/{id}/assignment") @SecurityRequirement(name="accessCookie")
 public AssignmentResponse assign(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestBody AssignmentRequest request){return answers.assign(id,p.userId(),request);}
 @PutMapping("/api/admin-answers/{id}") @SecurityRequirement(name="accessCookie")
 public AdminAnswerResponse update(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestBody AnswerUpdateRequest request){return answers.update(id,p.userId(),request);}
 @PutMapping("/api/admin-answers/{id}/status") @SecurityRequirement(name="accessCookie")
 public AdminAnswerResponse status(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestBody AnswerStatusRequest request){return answers.status(id,p.userId(),request);}
 @GetMapping("/api/me/admin-quota") @SecurityRequirement(name="accessCookie")
 public AdminQuotaResponse quota(@AuthenticationPrincipal SessionPrincipal p){return answers.quota(p.userId());}
 @GetMapping("/api/me/admin-answers") @SecurityRequirement(name="accessCookie")
 public PageResponse<AdminAnswerResponse> mine(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return answers.mine(p.userId(),page,size);}
 @GetMapping("/api/me/assignments") @SecurityRequirement(name="accessCookie")
 public PageResponse<AssignmentResponse> assignments(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return answers.assignments(p.userId(),page,size);}
}

