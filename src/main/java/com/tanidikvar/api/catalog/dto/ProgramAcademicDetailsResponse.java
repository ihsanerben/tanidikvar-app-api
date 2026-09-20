package com.tanidikvar.api.catalog.dto;

import java.util.UUID;

public record ProgramAcademicDetailsResponse(UUID academicUnitId,String faculty,
        Integer professorCount,Integer associateProfessorCount,Integer doctorFacultyMemberCount,
        Integer researchAssistantCount,String accreditationCode,String accreditationDescription,
        Integer minimumSuccessRank,Boolean tycQualified) { }
