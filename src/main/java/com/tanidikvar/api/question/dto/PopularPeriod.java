package com.tanidikvar.api.question.dto;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

public enum PopularPeriod {
    DAILY,WEEKLY,MONTHLY,YEARLY;

    public Instant start(Instant now,ZoneId zone) {
        var local=now.atZone(zone);
        LocalDate date=switch(this) {
            case DAILY -> local.toLocalDate();
            case WEEKLY -> local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> local.toLocalDate().withDayOfMonth(1);
            case YEARLY -> local.toLocalDate().withDayOfYear(1);
        };
        return date.atStartOfDay(zone).toInstant();
    }
}
