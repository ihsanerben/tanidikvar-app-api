package com.tanidikvar.api.answer.service;
import com.tanidikvar.api.answer.dto.AnswerResponse;
import com.tanidikvar.api.answer.repository.AnswerRepository;
import com.tanidikvar.api.answer.repository.AdminAnswerRepository;
import com.tanidikvar.api.answer.mapper.AnswerMapper;
import com.tanidikvar.api.answer.mapper.AdminAnswerMapper;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.question.service.QuestionAccessService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AnswerDetailService {
 private final AnswerRepository community; private final AdminAnswerRepository tanidik;
 private final AnswerMapper mapper; private final AdminAnswerMapper tanidikMapper; private final QuestionAccessService questions;
 public AnswerDetailService(AnswerRepository community,AdminAnswerRepository tanidik,AnswerMapper mapper,AdminAnswerMapper tanidikMapper,QuestionAccessService questions){this.community=community;this.tanidik=tanidik;this.mapper=mapper;this.tanidikMapper=tanidikMapper;this.questions=questions;}
 @Transactional(readOnly=true) public AnswerResponse get(UUID id,UUID viewer){
  var answer=community.find(id);
  if(answer.isPresent()){
   var value=answer.get();questions.requireReadable(value.questionId());
   if(value.deletedAt()!=null||value.moderatedAt()!=null)throw missing();return mapper.toResponse(value,viewer);
  }
  var value=tanidik.find(id).orElseThrow(this::missing);questions.requireReadable(value.questionId());
  if(value.deletedAt()!=null||value.moderatedAt()!=null)throw missing();var a=tanidikMapper.toResponse(value,viewer);
  return new AnswerResponse(a.id(),a.questionId(),a.authorId(),a.authorName(),a.avatarFileId(),a.educationStatus(),a.activeAdmin(),a.universityName(),a.departmentName(),"TANIDIK",a.body(),a.publishedAt(),a.editedAt(),a.deletedAt(),a.moderatedAt(),a.version(),a.owned(),a.anonymous());
 }
 private DomainException missing(){return new DomainException(404,"NOT_FOUND","Yorum bulunamadı.");}
}
