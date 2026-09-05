package com.tanidikvar.api.application.service;
import com.tanidikvar.api.application.dto.*;
import com.tanidikvar.api.file.service.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public class ApplicationUploadService {
 private final UploadValidation validation;private final FileService files;private final PrivateStorage storage;private final ApplicationService applications;
 public ApplicationUploadService(UploadValidation validation,FileService files,PrivateStorage storage,ApplicationService applications){this.validation=validation;this.files=files;this.storage=storage;this.applications=applications;}
 public ApplicationResponse submit(UUID owner,ApplicationSubmission request,MultipartFile document){
  var upload=validation.prepare(document,false);var old=applications.existing(owner,request,upload.sha256());if(old.isPresent())return old.get();
  var id=files.begin(owner,"VERIFICATION",upload);
  try{storage.write(id,upload.bytes());var result=applications.submit(owner,request,id,upload.sha256());if(!result.documentFileId().equals(id))files.fail(id);return result;}
  catch(RuntimeException ex){files.fail(id);throw ex;}
 }
}

