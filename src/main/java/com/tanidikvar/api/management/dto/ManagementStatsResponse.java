package com.tanidikvar.api.management.dto;
public record ManagementStatsResponse(long activeUsers,long disabledUsers,long activeAdmins,long pendingApplications,long activeQuestions,long archivedQuestions,long hiddenQuestions,long communityAnswers,long adminAnswers,long likes,long views) {}
