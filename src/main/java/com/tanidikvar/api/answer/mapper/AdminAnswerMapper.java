package com.tanidikvar.api.answer.mapper;
import com.tanidikvar.api.answer.entity.AdminAnswer;
import com.tanidikvar.api.answer.dto.AdminAnswerResponse;
import org.springframework.stereotype.Component;
@Component
public class AdminAnswerMapper {
 public AdminAnswerResponse toResponse(AdminAnswer a){boolean visible=a.authorName()!=null;return new AdminAnswerResponse(a.id(),a.questionId(),a.questionTitle(),visible?a.authorId():null,visible?a.authorName():"Katılımcı",visible&&a.activeAdmin(),visible?a.universityName():null,visible?a.departmentName():null,visible?a.educationStatus():null,visible?a.graduationYear():null,visible?a.avatarFileId():null,visible?a.occupation():null,visible?a.company():null,a.body(),a.publishedAt(),a.editedAt(),a.deletedAt(),a.moderatedAt(),a.version());}
}

