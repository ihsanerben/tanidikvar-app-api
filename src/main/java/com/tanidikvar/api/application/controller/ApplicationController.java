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
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
@RestController
public class ApplicationController {
 private final ApplicationService applications;
 public ApplicationController(ApplicationService applications){this.applications=applications;}
 @GetMapping({"/api/me/admin-applications","/api/me/tanidik-applications"})
 public PageResponse<ApplicationResponse> mine(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return applications.mine(p.userId(),page,size);}
 @PostMapping({"/api/me/admin-applications","/api/me/tanidik-applications"}) @ResponseStatus(HttpStatus.CREATED)
 public ApplicationResponse submit(@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestBody ApplicationSubmission request){return applications.submit(p.userId(),request);}
 @GetMapping({"/api/manager/admin-applications","/api/manager/tanidik-applications"}) @PreAuthorize("hasRole('MANAGER')")
 public PageResponse<ApplicationResponse> list(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return applications.manage(p.userId(),status,page,size);}
 @PutMapping({"/api/manager/admin-applications/{id}/decision","/api/manager/tanidik-applications/{id}/decision"}) @PreAuthorize("hasRole('MANAGER')")
 public ApplicationResponse decide(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody ApplicationDecision request){return applications.decide(p.userId(),id,request);}
 @PostMapping({"/api/manager/users/{id}/revoke-admin","/api/manager/users/{id}/revoke-tanidik"}) @PreAuthorize("hasRole('MANAGER')") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revoke(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody RevokeRequest request){applications.revoke(p.userId(),id,request);}
 @GetMapping({"/api/manager/admin-applications/{id}","/api/manager/tanidik-applications/{id}"}) @PreAuthorize("hasRole('MANAGER')")
 public ApplicationResponse detail(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return applications.detail(p.userId(),id);}
 @GetMapping("/api/manager/users/{id}/applications") @PreAuthorize("hasRole('MANAGER')")
 public PageResponse<ApplicationResponse> history(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return applications.history(p.userId(),id,page,size);}
}
