package com.tanidikvar.api.profile.mapper;
import com.tanidikvar.api.profile.entity.UserProfile;
import com.tanidikvar.api.profile.dto.ProfileResponse;
import com.tanidikvar.api.catalog.dto.EducationResponse;
import org.springframework.stereotype.Component;
@Component
public class ProfileMapper {
    public ProfileResponse toResponse(UserProfile p,EducationResponse education) {
        if(p==null || p.deletedAt()!=null) return new ProfileResponse(null,null,null,null,null,null,null,null,false,p==null?0:p.version());
        return new ProfileResponse(p.firstName(),p.lastName(),p.educationStatus(),education,p.graduationYear(),p.biography(),p.occupation(),p.company(),true,p.version());
    }
}
