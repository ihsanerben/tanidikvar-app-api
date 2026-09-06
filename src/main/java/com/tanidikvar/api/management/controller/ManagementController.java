package com.tanidikvar.api.management.controller;
import com.tanidikvar.api.management.dto.*;
import com.tanidikvar.api.management.service.ManagementService;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
@RestController @RequestMapping("/api/manager") @PreAuthorize("hasRole('MANAGER')")
public class ManagementController {
 private final ManagementService management;private final com.tanidikvar.api.management.service.ManagerWorkspaceService workspace;
 public ManagementController(ManagementService management,com.tanidikvar.api.management.service.ManagerWorkspaceService workspace){this.management=management;this.workspace=workspace;}
 @GetMapping("/statistics") public ManagementStatsResponse stats(@AuthenticationPrincipal SessionPrincipal p){return management.stats(p.userId());}
 @GetMapping("/actions") public PageResponse<ManagementActionResponse> actions(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String action,@RequestParam(defaultValue="") String targetType,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return workspace.actions(p.userId(),q,action,targetType,page,size);}
 @GetMapping("/users") public PageResponse<ManagedUserResponse> users(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(required=false) String q,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String authority,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return management.users(p.userId(),q,status,authority,page,size);}
 @PutMapping("/users/{id}/status") public ManagedUserResponse userStatus(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody ManagementStatusRequest request){return management.userStatus(p.userId(),id,request);}
 @GetMapping("/content") public PageResponse<ManagedContentResponse> contents(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="QUESTION") String kind,@RequestParam(required=false) String q,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return management.contents(p.userId(),kind,q,status,page,size);}
 @PutMapping("/content/{kind}/{id}/status") public ManagedContentResponse contentStatus(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@PathVariable String kind,@Valid @RequestBody ManagementStatusRequest request){return management.contentStatus(p.userId(),id,kind,request);}
}
