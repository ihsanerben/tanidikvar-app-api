package com.tanidikvar.api.catalog.controller;

import com.tanidikvar.api.catalog.dto.*;
import com.tanidikvar.api.catalog.service.ProgramCatalogService;
import com.tanidikvar.api.common.dto.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProgramCatalogController {
    private final ProgramCatalogService service;
    public ProgramCatalogController(ProgramCatalogService service){this.service=service;}
    @GetMapping({"/catalog-programs","/programs"}) public PageResponse<ProgramSummaryResponse> programs(
            @RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String programName,
            @RequestParam(defaultValue="")String universityName,@RequestParam(defaultValue="")String city,
            @RequestParam(defaultValue="")String institutionType,@RequestParam(defaultValue="")String degreeLevel,
            @RequestParam(defaultValue="")String scoreType,@RequestParam(required=false)Integer durationYears,
            @RequestParam(required=false)Integer rankFrom,@RequestParam(required=false)Integer rankTo,
            @RequestParam(required=false)java.math.BigDecimal scoreFrom,@RequestParam(required=false)java.math.BigDecimal scoreTo,
            @RequestParam(required=false)Boolean filled,@RequestParam(defaultValue="2025")Integer year,
            @RequestParam(defaultValue="")String faculty,@RequestParam(required=false)UUID universityId,
            @RequestParam(defaultValue="NAME")String sort,@RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="20")int size){return service.list(q,programName,universityName,city,institutionType,degreeLevel,scoreType,durationYears,rankFrom,rankTo,scoreFrom,scoreTo,filled,year,faculty,universityId,sort,page,size);}
    @GetMapping("/catalog-programs/{id}") public ProgramDetailResponse program(@PathVariable UUID id){return service.detail(id);}
    @GetMapping("/statistics/overview") public CatalogOverviewResponse overview(){return service.overview();}
    @GetMapping("/universities/{id}/catalog-statistics") public UniversityCatalogStatisticsResponse university(@PathVariable UUID id){return service.university(id);}
    @GetMapping("/programs/{id}/statistics") public ProgramDetailResponse programStatistics(@PathVariable UUID id){return service.detail(id);}
    @GetMapping("/statistics/cities") public java.util.List<LabelCountResponse> cities(){return service.cities();}
}
