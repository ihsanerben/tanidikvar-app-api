package com.tanidikvar.api.management.dto;
import java.time.LocalDate;
public record ManagementAnalyticsPointResponse(LocalDate date,long users,long questions,long communityAnswers,long adminAnswers,long views,long likes,long applications,long approvedApplications,long rejectedApplications) {}
