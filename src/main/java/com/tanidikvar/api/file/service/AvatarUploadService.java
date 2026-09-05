package com.tanidikvar.api.file.service;
import com.tanidikvar.api.file.dto.AvatarResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public class AvatarUploadService {
 private final UploadValidation validation;private final FileService files;private final PrivateStorage storage;
 public AvatarUploadService(UploadValidation validation,FileService files,PrivateStorage storage){this.validation=validation;this.files=files;this.storage=storage;}
 public AvatarResponse upload(UUID owner,MultipartFile file){var upload=validation.prepare(file,true);var id=files.begin(owner,"AVATAR",upload);try{storage.write(id,upload.bytes());return files.avatar(owner,id);}catch(RuntimeException ex){files.fail(id);throw ex;}}
}

