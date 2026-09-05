package com.tanidikvar.api.application.controller;
import com.tanidikvar.api.application.dto.*;
import com.tanidikvar.api.application.service.*;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
@RestController
public class ApplicationController {
 private final ApplicationService applications;private final ApplicationUploadService uploads;
 public ApplicationController(ApplicationService applications,ApplicationUploadService uploads){this.applications=applications;this.uploads=uploads;}
 @GetMapping("/api/me/admin-applications")
 public PageResponse<ApplicationResponse> mine(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return applications.mine(p.userId(),page,size);}
 @PostMapping(value="/api/me/admin-applications",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public ApplicationResponse submit(@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestPart("request") ApplicationSubmission request,@RequestPart("document") MultipartFile document){return uploads.submit(p.userId(),request,document);}
 @GetMapping("/api/manager/admin-applications") @PreAuthorize("hasRole('MANAGER')")
 public PageResponse<ApplicationResponse> list(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return applications.manage(p.userId(),status,page,size);}
 @PutMapping("/api/manager/admin-applications/{id}/decision") @PreAuthorize("hasRole('MANAGER')")
 public ApplicationResponse decide(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody ApplicationDecision request){return applications.decide(p.userId(),id,request);}
 @PostMapping("/api/manager/users/{id}/revoke-admin") @PreAuthorize("hasRole('MANAGER')") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revoke(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody RevokeRequest request){applications.revoke(p.userId(),id,request);}
}

