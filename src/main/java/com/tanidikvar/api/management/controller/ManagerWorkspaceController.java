package com.tanidikvar.api.management.controller;
import com.tanidikvar.api.management.dto.*;
import com.tanidikvar.api.management.service.ManagerWorkspaceService;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
@RestController @RequestMapping("/api/manager") @PreAuthorize("hasRole('MANAGER')")
public class ManagerWorkspaceController {
 private final ManagerWorkspaceService workspace;
 public ManagerWorkspaceController(ManagerWorkspaceService workspace){this.workspace=workspace;}
 @GetMapping("/account") public ManagerAccountResponse account(@AuthenticationPrincipal SessionPrincipal p){return workspace.account(p.userId());}
 @PutMapping("/account") public ManagerAccountResponse save(@AuthenticationPrincipal SessionPrincipal p,@Valid @RequestBody ManagerAccountRequest request){return workspace.saveAccount(p.userId(),request);}
 @GetMapping("/users/{id}") public ManagedUserDetailResponse user(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return workspace.user(p.userId(),id);}
 @GetMapping("/questions/{id}") public ManagedQuestionResponse question(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return workspace.question(p.userId(),id,page,size);}
 @PutMapping("/questions/{id}/classification") public ClassificationResponse classify(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody ClassificationRequest request){return workspace.classify(p.userId(),id,request);}
 @GetMapping("/catalog-usage/{kind}/{id}") public CatalogUsageResponse usage(@AuthenticationPrincipal SessionPrincipal p,@PathVariable String kind,@PathVariable UUID id){return workspace.usage(p.userId(),kind,id);}
 @GetMapping("/actions/{id}") public ActionDetailResponse action(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return workspace.action(p.userId(),id);}
}
