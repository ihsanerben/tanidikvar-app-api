package com.tanidikvar.api.catalog.controller;

import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.catalog.dto.*;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasRole('MANAGER')")
@SecurityRequirement(name="accessCookie")
public class ManagerCatalogController {
    private final CatalogService catalog;
    public ManagerCatalogController(CatalogService catalog) { this.catalog=catalog; }
    @GetMapping("/catalog/{kind}") public PageResponse<CatalogResponse> list(@PathVariable CatalogKind kind,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean includeDeleted,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.list(kind,q,includeDeleted,page,size); }
    @PostMapping("/catalog/{kind}") @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponse create(@AuthenticationPrincipal SessionPrincipal principal,@PathVariable CatalogKind kind,@Valid @RequestBody CatalogCreateRequest request) {
        return catalog.create(principal.userId(),kind,request.name());
    }
    @PutMapping("/catalog/{kind}/{id}") public CatalogResponse rename(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable CatalogKind kind,@PathVariable UUID id,@Valid @RequestBody CatalogUpdateRequest request) {
        return catalog.rename(principal.userId(),kind,id,request);
    }
    @PutMapping("/catalog/{kind}/{id}/status") public CatalogResponse status(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable CatalogKind kind,@PathVariable UUID id,@Valid @RequestBody CatalogStatusRequest request) {
        return catalog.status(principal.userId(),kind,id,request);
    }
    @GetMapping("/university-departments") public PageResponse<EducationResponse> education(@RequestParam UUID universityId,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean includeDeleted,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return catalog.educationList(universityId,q,includeDeleted,page,size);
    }
    @PostMapping("/university-departments") @ResponseStatus(HttpStatus.CREATED)
    public EducationResponse createEducation(@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody EducationCreateRequest request) {
        return catalog.createEducation(principal.userId(),request);
    }
    @PutMapping("/university-departments/{id}/status") public EducationResponse educationStatus(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id,@Valid @RequestBody CatalogStatusRequest request) { return catalog.educationStatus(principal.userId(),id,request); }
}
