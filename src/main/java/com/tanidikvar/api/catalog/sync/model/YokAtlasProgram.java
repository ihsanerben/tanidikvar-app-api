package com.tanidikvar.api.catalog.sync.model;

import java.util.List;

public record YokAtlasProgram(
        Long osymGuideId,
        Long sourceProgramId,
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
        List<YokAtlasYearStats> statistics,
        String sourcePayload) {
    public YokAtlasProgram(Long osymGuideId,Long sourceProgramId,String guideCode,long universityId,
            String universityName,String universityType,String universityCity,Long academicUnitId,
            String academicUnitName,String academicUnitType,String city,String district,long programGroupId,
            String programGroupName,String displayName,String degreeLevel,String scoreType,String educationType,
            String language,String scholarship,Integer durationYears,List<YokAtlasYearStats> statistics) {
        this(osymGuideId,sourceProgramId,guideCode,universityId,universityName,universityType,universityCity,
                academicUnitId,academicUnitName,academicUnitType,city,district,programGroupId,programGroupName,
                displayName,degreeLevel,scoreType,educationType,language,scholarship,durationYears,statistics,null);
    }
}
