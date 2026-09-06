package com.tanidikvar.api.profile.mapper;
import com.tanidikvar.api.profile.entity.UserProfile;
import com.tanidikvar.api.profile.dto.ProfileResponse;
import com.tanidikvar.api.catalog.dto.EducationResponse;
import com.tanidikvar.api.file.repository.FileRepository;
import org.springframework.stereotype.Component;
@Component
public class ProfileMapper {
    private final FileRepository files;
    public ProfileMapper(FileRepository files) { this.files=files; }
    public ProfileResponse toResponse(UserProfile p,EducationResponse education) {
        if(p==null || p.deletedAt()!=null) return new ProfileResponse(null,null,null,null,null,null,null,null,null,null,false,p==null?0:p.version());
        boolean completed=files.avatar(p.userId()).isPresent();
        return new ProfileResponse(p.firstName(),p.lastName(),p.educationStatus(),education,p.graduationYear(),p.biography(),p.occupation(),p.company(),p.linkedinUrl(),p.portfolioUrl(),completed,p.version());
    }
}
