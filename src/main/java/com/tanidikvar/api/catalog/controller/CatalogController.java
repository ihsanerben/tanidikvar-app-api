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
    @GetMapping("/universities") public PageResponse<CatalogResponse> universities(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.list(CatalogKind.UNIVERSITY,q,false,page,size); }
    @GetMapping("/departments") public PageResponse<CatalogResponse> departments(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.list(CatalogKind.DEPARTMENT,q,false,page,size); }
    @GetMapping("/tags") public PageResponse<CatalogResponse> tags(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return catalog.list(CatalogKind.TAG,q,false,page,size); }
    @GetMapping("/universities/{id}/departments") public PageResponse<EducationResponse> education(@PathVariable UUID id,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return catalog.educationList(id,q,false,page,size);
    }
    @PostMapping("/tags") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CatalogResponse tag(@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody CatalogCreateRequest request) {
        return catalog.create(principal.userId(),CatalogKind.TAG,request.name());
    }
}
