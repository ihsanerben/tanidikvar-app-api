package com.tanidikvar.api.catalog.controller;

import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.catalog.dto.*;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog=catalog; }
    @GetMapping("/universities") public PageResponse<UniversityResponse> universities(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String city,@RequestParam(defaultValue="") String institutionType,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.universities(q,city,institutionType,page,size); }
    @GetMapping("/universities/{id}") public UniversityResponse university(@PathVariable UUID id) {
        return catalog.university(id);
    }
    @PutMapping("/manager/universities/{id}/details") public UniversityResponse universityDetails(@AuthenticationPrincipal SessionPrincipal principal,@PathVariable UUID id,@Valid@RequestBody UniversityDetailsRequest request){return catalog.universityDetails(principal.userId(),id,request);}
    @GetMapping("/departments") public PageResponse<CatalogResponse> departments(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.list(CatalogKind.DEPARTMENT,q,false,page,size); }
    @GetMapping("/programs") public PageResponse<EducationResponse> programs(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.publicPrograms(q,page,size); }
    @GetMapping("/tags") public PageResponse<CatalogResponse> tags(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.list(CatalogKind.TAG,q,false,page,size); }
    @GetMapping("/universities/{id}/departments") public PageResponse<EducationResponse> education(@PathVariable UUID id,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return catalog.educationList(id,q,false,page,size);
    }
    @GetMapping("/universities/{universityId}/departments/{departmentId}") public EducationResponse education(
            @PathVariable UUID universityId,@PathVariable UUID departmentId) {
        return catalog.publicEducation(universityId,departmentId);
    }
    @PostMapping("/tags") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('MANAGER')")
    public CatalogResponse tag(@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody CatalogCreateRequest request) {
        return catalog.create(principal.userId(),CatalogKind.TAG,request.name(),request.reason());
    }
}
