package com.tanidikvar.api.catalog.sync.dto;

public record YokCatalogQualityReport(
        int universityCount,
        int programFamilyCount,
        int programCount,
        int optionCount,
        int cityCount,
        int stateUniversityCount,
        int foundationUniversityCount,
        int undergraduateOptionCount,
        int associateOptionCount,
        int optionsWithoutAcademicUnit,
        int optionsWithoutCurrentSuccessRank,
        int existingSourceUniversityCount,
        int newUniversityCount,
        int manualNameCollisionCount
) { }
