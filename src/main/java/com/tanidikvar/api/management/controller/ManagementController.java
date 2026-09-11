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
 private final com.tanidikvar.api.report.service.QuestionReportService reports;
 public ManagementController(ManagementService management,com.tanidikvar.api.management.service.ManagerWorkspaceService workspace,com.tanidikvar.api.report.service.QuestionReportService reports){this.management=management;this.workspace=workspace;this.reports=reports;}
 @GetMapping("/reports") public PageResponse<com.tanidikvar.api.report.dto.QuestionReportResponse> reports(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="NEWEST") String sort,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return reports.list(p.userId(),q,status,sort,page,size);}
 @PutMapping("/reports/{id}") public com.tanidikvar.api.report.dto.QuestionReportResponse decideReport(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody com.tanidikvar.api.report.dto.QuestionReportDecision request){return reports.decide(p.userId(),id,request);}
 @GetMapping("/statistics") public ManagementStatsResponse stats(@AuthenticationPrincipal SessionPrincipal p){return management.stats(p.userId());}
 @GetMapping("/analytics") public ManagementAnalyticsResponse analytics(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(required=false) java.time.LocalDate dateFrom,@RequestParam(required=false) java.time.LocalDate dateTo){return management.analytics(p.userId(),dateFrom,dateTo);}
 @GetMapping("/actions") public PageResponse<ManagementActionResponse> actions(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String action,@RequestParam(defaultValue="") String targetType,@RequestParam(required=false) java.time.LocalDate dateFrom,@RequestParam(required=false) java.time.LocalDate dateTo,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return workspace.actions(p.userId(),q,action,targetType,dateFrom,dateTo,page,size);}
 @GetMapping("/users") public PageResponse<ManagedUserResponse> users(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(required=false) String q,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String authority,@RequestParam(defaultValue="") String educationStatus,@RequestParam(defaultValue="CREATED_DESC") String sort,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return management.users(p.userId(),q,status,authority,educationStatus,sort,page,size);}
 @PutMapping("/users/{id}/status") public ManagedUserResponse userStatus(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@Valid @RequestBody ManagementStatusRequest request){return management.userStatus(p.userId(),id,request);}
 @GetMapping("/content") public PageResponse<ManagedContentResponse> contents(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="QUESTION") String kind,@RequestParam(required=false) String q,@RequestParam(defaultValue="ALL") String status,@RequestParam(required=false) UUID authorId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return management.contents(p.userId(),kind,q,status,authorId,page,size);}
 @PutMapping("/content/{kind}/{id}/status") public ManagedContentResponse contentStatus(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@PathVariable String kind,@Valid @RequestBody ManagementStatusRequest request){return management.contentStatus(p.userId(),id,kind,request);}
 @PutMapping("/content/{kind}/{id}") public ManagedContentResponse editContent(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@PathVariable String kind,@Valid @RequestBody ManagementContentEditRequest request){return management.editContent(p.userId(),id,kind,request);}
}
