package com.tanidikvar.api.answer.mapper;
import com.tanidikvar.api.answer.dto.AnswerResponse;
import com.tanidikvar.api.answer.entity.Answer;
import org.springframework.stereotype.Component;
@Component
public class AnswerMapper {
    public AnswerResponse toResponse(Answer a) {
        return new AnswerResponse(a.id(),a.questionId(),a.authorName()==null?null:a.authorId(),a.authorName()==null?"Katılımcı":a.authorName(),
                a.authorName()==null?null:a.avatarFileId(),a.authorName()==null?null:a.educationStatus(),"COMMUNITY",a.body(),a.publishedAt(),a.editedAt(),a.deletedAt(),a.moderatedAt(),a.version());
    }
}
