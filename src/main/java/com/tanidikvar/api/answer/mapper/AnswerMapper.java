package com.tanidikvar.api.answer.mapper;
import com.tanidikvar.api.answer.dto.AnswerResponse;
import com.tanidikvar.api.answer.entity.Answer;
import org.springframework.stereotype.Component;
@Component
public class AnswerMapper {
    public AnswerResponse toResponse(Answer a) { return toResponse(a,null); }
    public AnswerResponse toResponse(Answer a,java.util.UUID viewer) {
        boolean visible=a.authorName()!=null&&!a.anonymous();
        return new AnswerResponse(a.id(),a.questionId(),visible?a.authorId():null,
                a.anonymous()?"Anonim Tanıdık":visible?a.authorName():"Katılımcı",
                visible?a.avatarFileId():null,visible?a.educationStatus():null,a.anonymous()||visible&&a.activeAdmin(),
                visible?a.universityName():null,visible?a.departmentName():null,"COMMUNITY",a.body(),a.publishedAt(),
                a.editedAt(),a.deletedAt(),a.moderatedAt(),a.version(),a.authorId().equals(viewer),a.anonymous());
    }
}
