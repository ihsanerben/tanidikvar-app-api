package com.tanidikvar.api.catalog.sync.model;

import java.util.List;

public record YokAtlasProgram(
        Long osymGuideId,
        long sourceProgramId,
        String guideCode,
        long universityId,
        String universityName,
        String universityType,
        String universityCity,
        Long academicUnitId,
        String academicUnitName,
        String academicUnitType,
        String city,
        String district,
        long programGroupId,
        String programGroupName,
        String displayName,
        String degreeLevel,
        String scoreType,
        String educationType,
        String language,
        String scholarship,
        Integer durationYears,
        List<YokAtlasYearStats> statistics) { }
