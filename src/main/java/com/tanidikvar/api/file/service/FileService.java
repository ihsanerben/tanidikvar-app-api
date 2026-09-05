package com.tanidikvar.api.file.service;
import com.tanidikvar.api.file.repository.FileRepository;
import com.tanidikvar.api.file.dto.*;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.auth.entity.Authority;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.service.InteractionPolicy;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class FileService {
 private final FileRepository files;private final AccountAccessService accounts;private final InteractionPolicy profiles;private final PrivateStorage storage;
 public FileService(FileRepository files,AccountAccessService accounts,InteractionPolicy profiles,PrivateStorage storage){this.files=files;this.accounts=accounts;this.profiles=profiles;this.storage=storage;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public UUID begin(UUID owner,String purpose,PreparedUpload upload){accounts.lockActive(owner);profiles.requireCompleted(owner);files.recover();if(files.recent(owner)>=20)throw new DomainException(429,"RATE_LIMITED","Bir saat içinde en fazla 20 dosya yükleyebilirsin.");UUID id=UUID.randomUUID();files.create(id,owner,purpose,upload.contentType(),upload.bytes().length);return id;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void fail(UUID id){files.fail(id);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void ready(UUID id){files.ready(id);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void requireVerification(UUID id,UUID owner){var file=files.find(id).orElseThrow(this::missing);if(!file.purpose().equals("VERIFICATION")||!file.ownerId().equals(owner))throw missing();}
 @Transactional
 public AvatarResponse avatar(UUID owner,UUID file){accounts.lockActive(owner);profiles.requireCompleted(owner);files.removeAvatar(owner);files.ready(file);return new AvatarResponse(file);}
 @Transactional
 public void removeAvatar(UUID owner){accounts.lockActive(owner);profiles.requireCompleted(owner);files.removeAvatar(owner);}
 @Transactional(readOnly=true)
 public AvatarResponse avatarInfo(UUID owner){return new AvatarResponse(files.avatar(owner).orElse(null));}
 @Transactional(readOnly=true)
 public FileDownload avatarDownload(UUID id){var file=files.find(id).orElseThrow(this::missing);if(!file.purpose().equals("AVATAR")||!files.avatar(file.ownerId()).filter(id::equals).isPresent())throw missing();return new FileDownload(storage.read(file.storageKey()),file.contentType(),"avatar.png");}
 @Transactional
 public FileDownload document(UUID id,UUID actor){var account=accounts.lockActive(actor);var file=files.find(id).orElseThrow(this::missing);if(!file.purpose().equals("VERIFICATION")||(!actor.equals(file.ownerId())&&account.getAuthority()!=Authority.MANAGER))throw missing();return new FileDownload(storage.read(file.storageKey()),file.contentType(),"belge.pdf");}
 private DomainException missing(){return new DomainException(404,"NOT_FOUND","Dosya bulunamadı.");}
}
