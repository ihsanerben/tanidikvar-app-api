package com.tanidikvar.api.catalog.sync.model;

import java.math.BigDecimal;

public record YokAtlasYearStats(
        int year,
        Integer quota,
        Integer placed,
        BigDecimal minimumScore,
        Integer successRank,
        BigDecimal maximumScore,
        Integer placedMale,
        Integer placedFemale,
        BigDecimal averageSecondaryScore,
        Integer totalPreferences,
        BigDecimal demandPerQuota,
        BigDecimal averagePreferenceRank,
        Integer quotaGeneral,
        Integer quotaSchoolFirst,
        Integer quotaMartyrVeteran,
        Integer quotaWoman34Plus,
        Integer quotaEarthquake,
        String statisticsSource,
        String payloadChecksum) {
    public YokAtlasYearStats(int year,Integer quota,Integer placed,BigDecimal minimumScore,Integer successRank,String payloadChecksum) {
        this(year,quota,placed,minimumScore,successRank,null,null,null,null,null,null,null,null,null,null,null,null,null,payloadChecksum);
    }
}
