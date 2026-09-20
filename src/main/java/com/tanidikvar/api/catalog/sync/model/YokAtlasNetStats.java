package com.tanidikvar.api.catalog.sync.model;

import java.math.BigDecimal;

public record YokAtlasNetStats(
        String guideCode,int year,BigDecimal minimumScore,BigDecimal averageSecondaryScore,
        BigDecimal coefficient,BigDecimal tytTurkish,BigDecimal tytSocial,BigDecimal tytMath,
        BigDecimal tytScience,BigDecimal aytMath,BigDecimal aytPhysics,BigDecimal aytChemistry,
        BigDecimal aytBiology,BigDecimal aytLiterature,BigDecimal aytHistory1,BigDecimal aytGeography1,
        BigDecimal aytHistory2,BigDecimal aytGeography2,BigDecimal aytPhilosophy,
        BigDecimal aytReligion,BigDecimal foreignLanguage,String payloadChecksum,String sourcePayload) {
    public YokAtlasNetStats(String guideCode,int year,BigDecimal minimumScore,BigDecimal averageSecondaryScore,
            BigDecimal coefficient,BigDecimal tytTurkish,BigDecimal tytSocial,BigDecimal tytMath,
            BigDecimal tytScience,BigDecimal aytMath,BigDecimal aytPhysics,BigDecimal aytChemistry,
            BigDecimal aytBiology,BigDecimal aytLiterature,BigDecimal aytHistory1,BigDecimal aytGeography1,
            BigDecimal aytHistory2,BigDecimal aytGeography2,BigDecimal aytPhilosophy,
            BigDecimal aytReligion,BigDecimal foreignLanguage,String payloadChecksum) {
        this(guideCode,year,minimumScore,averageSecondaryScore,coefficient,tytTurkish,tytSocial,tytMath,
                tytScience,aytMath,aytPhysics,aytChemistry,aytBiology,aytLiterature,aytHistory1,
                aytGeography1,aytHistory2,aytGeography2,aytPhilosophy,aytReligion,foreignLanguage,
                payloadChecksum,null);
    }
}
