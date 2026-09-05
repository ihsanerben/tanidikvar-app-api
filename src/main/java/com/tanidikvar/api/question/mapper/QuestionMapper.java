package com.tanidikvar.api.question.mapper;
import com.tanidikvar.api.question.dto.*;
import com.tanidikvar.api.question.entity.Question;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class QuestionMapper {
    public QuestionResponse toResponse(Question q,List<QuestionTagResponse> tags) {
        return new QuestionResponse(q.id(),q.authorName()==null?null:q.authorId(),q.authorName()==null?"Katılımcı":q.authorName(),
                q.title(),q.body(),q.scope(),q.displayUniversityId(),q.universityName(),q.universityDepartmentId(),
                q.departmentId(),q.departmentName(),tags,q.createdAt(),q.editedAt(),q.archivedAt(),q.version());
    }
}
