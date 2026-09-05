package com.tanidikvar.api.answer.entity;
import java.time.Instant;
import java.util.UUID;
public record AdminAnswer(UUID id,UUID questionId,String questionTitle,UUID authorId,String authorName,boolean activeAdmin,String universityName,String departmentName,String educationStatus,Integer graduationYear,UUID avatarFileId,String occupation,String company,String body,Instant publishedAt,Instant editedAt,Instant deletedAt, Instant moderatedAt,long version) {}

