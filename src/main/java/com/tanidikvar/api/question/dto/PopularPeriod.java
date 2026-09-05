package com.tanidikvar.api.question.dto;
public enum PopularPeriod {
    DAILY(1),WEEKLY(7),MONTHLY(30),YEARLY(365);
    private final long seconds;
    PopularPeriod(int days) {this.seconds=days*86400L;}
    public long seconds(){return seconds;}
}
