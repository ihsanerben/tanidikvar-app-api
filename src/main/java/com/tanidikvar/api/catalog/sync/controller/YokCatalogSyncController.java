package com.tanidikvar.api.catalog.sync.controller;

import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.catalog.sync.dto.YokCatalogSyncResponse;
import com.tanidikvar.api.catalog.sync.service.YokCatalogSyncService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/catalog/dataset-sync")
@PreAuthorize("hasRole('MANAGER')")
@SecurityRequirement(name="accessCookie")
public class YokCatalogSyncController {
    private final YokCatalogSyncService service;
    public YokCatalogSyncController(YokCatalogSyncService service){this.service=service;}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public YokCatalogSyncResponse start(@AuthenticationPrincipal SessionPrincipal principal){return service.start(principal.userId());}

    @PostMapping("/preview")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public YokCatalogSyncResponse preview(@AuthenticationPrincipal SessionPrincipal principal){return service.preview(principal.userId());}

    @GetMapping("/{id}")
    public YokCatalogSyncResponse get(@PathVariable UUID id){return service.get(id);}
    @GetMapping public java.util.List<YokCatalogSyncResponse> history(){return service.history();}
}
