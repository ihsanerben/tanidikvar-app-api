package com.tanidikvar.api.application.service;
import com.tanidikvar.api.application.repository.ApplicationRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class VerificationAccessService {
 private final ApplicationRepository applications;
 public VerificationAccessService(ApplicationRepository applications){this.applications=applications;}
 @Transactional(readOnly=true)
 public boolean approved(UUID owner,UUID id){return id!=null&&applications.find(id).filter(a->a.applicantId().equals(owner)&&a.status().equals("APPROVED")).isPresent();}
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID requireApproved(UUID owner,UUID id){if(!approved(owner,id))throw new DomainException(403,"ADMIN_REQUIRED","Güncel Admin doğrulaması gerekiyor.");return id;}
}

