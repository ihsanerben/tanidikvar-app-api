package com.tanidikvar.api.management.entity;
import java.time.LocalDate;
public record ManagementAnalyticsPoint(LocalDate date,long users,long questions,long communityAnswers,long adminAnswers,long views,long likes,long applications,long approvedApplications,long rejectedApplications) {}
