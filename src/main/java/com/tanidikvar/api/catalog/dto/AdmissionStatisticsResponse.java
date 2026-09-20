package com.tanidikvar.api.catalog.dto;

import java.math.BigDecimal;

public record AdmissionStatisticsResponse(int year,Integer quota,Integer placed,BigDecimal minimumScore,
        BigDecimal maximumScore,Integer successRank,Integer placedMale,Integer placedFemale,
        BigDecimal averageSecondaryScore,Integer totalPreferences,BigDecimal demandPerQuota,
        BigDecimal averagePreferenceRank,BigDecimal scoreCoefficient,BigDecimal tytTurkishNet,
        BigDecimal tytSocialNet,BigDecimal tytMathNet,BigDecimal tytScienceNet,BigDecimal aytMathNet,
        BigDecimal aytPhysicsNet,BigDecimal aytChemistryNet,BigDecimal aytBiologyNet,
        BigDecimal aytLiteratureNet,BigDecimal aytHistory1Net,BigDecimal aytGeography1Net,
        BigDecimal aytHistory2Net,BigDecimal aytGeography2Net,BigDecimal aytPhilosophyNet,
        BigDecimal aytReligionNet,BigDecimal foreignLanguageNet) { }
