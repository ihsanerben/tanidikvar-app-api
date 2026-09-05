package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.profile.repository.PublicAdminProfileRepository;
import com.tanidikvar.api.profile.mapper.PublicAdminProfileMapper;
import com.tanidikvar.api.profile.dto.PublicAdminProfileResponse;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.dto.SearchQuery;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PublicAdminProfileService {
 private final PublicAdminProfileRepository profiles;private final PublicAdminProfileMapper mapper;
 public PublicAdminProfileService(PublicAdminProfileRepository profiles,PublicAdminProfileMapper mapper){this.profiles=profiles;this.mapper=mapper;}
 @Transactional(readOnly=true)
 public PublicAdminProfileResponse get(UUID id){return profiles.find(id).map(mapper::toResponse).orElseThrow(()->new DomainException(404,"NOT_FOUND","Admin profili bulunamadı."));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResponse<PublicAdminProfileResponse> search(String query,int page,int size) {
  SearchQuery.page(page,size);String q=SearchQuery.clean(query);
  return new PageResponse<>(profiles.search(q,page,size).stream().map(mapper::toResponse).toList(),page,size,profiles.count(q));
 }

}

