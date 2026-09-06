package com.tanidikvar.api.profile.mapper;
import com.tanidikvar.api.profile.entity.PublicProfile;
import com.tanidikvar.api.profile.dto.PublicProfileResponse;
import org.springframework.stereotype.Component;
@Component
public class PublicProfileMapper {
 public PublicProfileResponse toResponse(PublicProfile p){return new PublicProfileResponse(p.id(),p.name(),p.email(),p.role(),p.educationStatus(),p.universityName(),p.departmentName(),p.graduationYear(),p.biography(),p.occupation(),p.company(),p.linkedinUrl(),p.portfolioUrl(),p.avatarFileId());}
}
