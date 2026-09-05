package com.tanidikvar.api.profile.mapper;
import com.tanidikvar.api.profile.entity.PublicAdminProfile;
import com.tanidikvar.api.profile.dto.PublicAdminProfileResponse;
import org.springframework.stereotype.Component;
@Component
public class PublicAdminProfileMapper {
 public PublicAdminProfileResponse toResponse(PublicAdminProfile p){return new PublicAdminProfileResponse(p.id(),p.name(),p.activeAdmin(),p.universityName(),p.departmentName(),p.educationStatus(),p.graduationYear(),p.biography(),p.occupation(),p.company(),p.avatarFileId(),p.answerCount());}
}

