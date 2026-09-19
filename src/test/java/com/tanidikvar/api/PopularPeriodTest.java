package com.tanidikvar.api;

import com.tanidikvar.api.question.dto.PopularPeriod;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PopularPeriodTest {
    private static final ZoneId ISTANBUL=ZoneId.of("Europe/Istanbul");
    private static final Instant SATURDAY=Instant.parse("2026-09-05T12:00:00Z");

    @Test void periodsStartAtIstanbulCalendarBoundaries() {
        assertThat(PopularPeriod.DAILY.start(SATURDAY,ISTANBUL)).isEqualTo(Instant.parse("2026-09-04T21:00:00Z"));
        assertThat(PopularPeriod.WEEKLY.start(SATURDAY,ISTANBUL)).isEqualTo(Instant.parse("2026-08-30T21:00:00Z"));
        assertThat(PopularPeriod.MONTHLY.start(SATURDAY,ISTANBUL)).isEqualTo(Instant.parse("2026-08-31T21:00:00Z"));
        assertThat(PopularPeriod.YEARLY.start(SATURDAY,ISTANBUL)).isEqualTo(Instant.parse("2025-12-31T21:00:00Z"));
    }
}
