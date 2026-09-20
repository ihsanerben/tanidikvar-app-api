package com.tanidikvar.api.catalog.dto;

import java.util.List;

public record ProgramDetailResponse(ProgramSummaryResponse summary,
        List<ProgramAcademicDetailsResponse> academicDetails,List<AdmissionOptionResponse> options) { }
